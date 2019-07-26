@file:Suppress("unused")

package com.github.ppaszkiewicz.tools.toolbox.reflection

import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/*
 * Reflection helpers and delegates. Note they all use JAVA reflection (don't need kotlin reflect).
 */

/**
 * Interface for field reflection, through extensions or delegation operators.
 *
 * This does not have any error handling, preferably only use for imported libraries where classes are fixed
 * and won't change until update.
 * */
interface IFieldReflector {
    /**
     * Object that is used as a target for reflection.
     *
     * If this is null, delegates work on self instead (assumes IFieldReflector is extending reflected class).
     * */
    val reflectTargetObject: Any?
        get() = null
    /** Super class name that will be reflected for declared fields. */
    val reflectTargetClass: Class<*>
    /** Reflect field cache - can be instance for singletons, or map in an object. */
    val reflectedFieldCache: HashMap<String, Field>

    /** Reflects field from [reflectTargetClass]. */
    operator fun <T> getValue(thisRef: Any, property: KProperty<*>): T =
        reflectGet(reflectTargetObject ?: thisRef, property.name)

    /** Reflects field from [reflectTargetClass]. */
    operator fun <T> setValue(thisRef: Any, property: KProperty<*>, value: T) =
        reflectSet(reflectTargetObject ?: thisRef, property.name, value)
}

/** Generate default [IFieldReflector] implementation for class [T]. */
inline fun <reified T> FieldReflector(reflectTargetObject: T? = null) = object : IFieldReflector {
    override val reflectTargetObject: T? = reflectTargetObject
    override val reflectTargetClass: Class<*> = T::class.java
    override val reflectedFieldCache: HashMap<String, Field> = HashMap()
}

/** Delegate to access single field of reflected class - use if there's field name collision. */
fun <T> IFieldReflector.field(fieldName: String) =  SingleFieldReflector<T>(this, fieldName)

/** Get field from [IFieldReflector.reflectedFieldCache] or reflect it and make it accessible. */
fun IFieldReflector.getOrReflectField(name: String) = reflectedFieldCache.getOrPut(name) {
    reflectTargetClass.getDeclaredField(name).apply { isAccessible = true }
}

/** Get field with given name from [sourceObject], using field cache of this [IFieldReflector]. */
@Suppress("UNCHECKED_CAST")
fun <T> IFieldReflector.reflectGet(sourceObject: Any, fieldName: String) =
    getOrReflectField(fieldName).get(sourceObject) as T

/** Set field of [targetObject] with [fieldName] to value, using field cache of this [IFieldReflector]. */
fun <T> IFieldReflector.reflectSet(targetObject: Any, fieldName: String, value: T) {
    getOrReflectField(fieldName).set(targetObject, value)
}

/** Reflect default constructor and create new instance of this object.*/
fun <T : Any> KClass<T>.newInstance(): T =
    this.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

/** Reflection delegate for a single field. This is only needed if it's not possible to use property name directly.  */
class SingleFieldReflector<T>(val delegateTarget: IFieldReflector, val fieldName: String) :
    ReadWriteProperty<Any, T> {

    // internal - caches field using property name instead of field name itself to prevent clashes
    private fun getOrReflectField(propertyName: String) =
        delegateTarget.reflectedFieldCache.getOrPut(propertyName){
            delegateTarget.reflectTargetClass.getDeclaredField(fieldName).apply { isAccessible = true }
        }

    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getOrReflectField(property.name).get(delegateTarget.reflectTargetObject) as T

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        getOrReflectField(property.name).set(delegateTarget.reflectTargetObject, value)
    }
}

/*
    ----------------- METHOD REFLECTION BELOW ---------------
 */

/// reflect by using invoke operator - comment out if it collides with your custom invoke implementation

/**
 * Create delegate for method from [IFieldReflector.reflectTargetClass], using property name only.
 *
 * Return value will not be cast.
 * */
@JvmName("invokeAny")
operator fun IFieldReflector.invoke() = reflectMethod()

/**
 * Create delegate for method from [IFieldReflector.reflectTargetClass], using property name only.
 *
 * Method must return values of type [T].
 * */
operator fun <T> IFieldReflector.invoke() = reflectMethod<T>()

/**
 * Create delegate for method from [IFieldReflector.reflectTargetClass].
 * */
operator fun <T> IFieldReflector.invoke(methodName: String, vararg params: Class<*>) =
    reflectMethod<T>(methodName, *params)

/**
 * Create delegate for method from [IFieldReflector.reflectTargetClass]. It will use property name
 * to determine method name.
 * */
operator fun <T> IFieldReflector.invoke(vararg params: Class<*>) = reflectMethod<T>(*params)

//// non ambigous calls

/**
 * Create delegate for method from [IFieldReflector.reflectTargetClass], using property name only.
 *
 * Return value will not be cast.
 * */
@JvmName("reflectMethodAny")
fun IFieldReflector.reflectMethod() = reflectMethod<Any>()

/**
 * Create delegate for method from [IFieldReflector.reflectTargetClass], using property name only.
 *
 * Method must return values of type [T].
 * */
fun <T> IFieldReflector.reflectMethod() = MethodReflector<T>(this, null)

/**
 * Create delegate for method from [IFieldReflector.reflectTargetClass].
 * */
fun <T> IFieldReflector.reflectMethod(methodName: String, vararg params: Class<*>) =
    MethodReflector<T>(this, methodName, *params)

/**
 * Create delegate for method from [IFieldReflector.reflectTargetClass]. It will use property name
 * to determine method name.
 * */
fun <T> IFieldReflector.reflectMethod(vararg params: Class<*>) =
    MethodReflector<T>(this, null, *params)

/**
 * Reflect method from [IFieldReflector.reflectTargetClass] without cache using name only.
 *
 * *(use only if method has no overloads so it's not ambiguous)*.
 **/
fun <T> IFieldReflector.reflectMethodOnce(methodName: String): MMethod<T> {
    val m = reflectTargetClass.declaredMethods.find { it.name == methodName }!!.apply {
        isAccessible = true
    }
    return MMethod(reflectTargetObject ?: this, m)
}

/** Reflect method from [IFieldReflector.reflectTargetClass] without cache. */
fun <T> IFieldReflector.reflectMethodOnce(methodName: String, vararg params: Class<*>): MMethod<T> {
    val m = reflectTargetClass.getDeclaredMethod(methodName, *params).apply { isAccessible = true }
    return MMethod(reflectTargetObject ?: this, m)
}


/** Reflection delegate for a method. */
class MethodReflector<T>(val delegateTarget: IFieldReflector, val methodName: String?, vararg val params: Class<*>) :
    ReadOnlyProperty<Any, MMethod<T>> {
    private var mMethod: MMethod<T>? = null
    override fun getValue(thisRef: Any, property: KProperty<*>): MMethod<T> {
        if (mMethod == null) {
            mMethod = if (params.isEmpty())
                delegateTarget.reflectMethodOnce(methodName ?: property.name)
            else
                delegateTarget.reflectMethodOnce(methodName ?: property.name, *params)
        }
        return mMethod!!
    }
}

/** [Method] wrapper with custom [invoke]. [T] is type of data returned from [method]. */
class MMethod<T>(
    /** Object invoking reflected method. */
    val reflectTargetObject: Any,
    /** Wrapped reflected method. */
    val method: Method
) {
    operator fun invoke(vararg args: Any?) = method(reflectTargetObject, *args) as T
}