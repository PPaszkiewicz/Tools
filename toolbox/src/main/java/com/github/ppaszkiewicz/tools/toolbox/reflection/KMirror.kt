@file:Suppress("unused")

package com.github.ppaszkiewicz.tools.toolbox.reflection

import java.lang.reflect.Field
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/*
 * Reflection helpers and delegates. Note they all use JAVA reflection (don't need kotlin reflect).
 */

/**
 * **KMirror** -  Kotlin mirror which provides reflection.
 *
 * Interface for declared field reflection, through extensions or delegation operators.
 *
 * This does not have any error handling, only use for imported libraries where classes are fixed
 * and won't change until explicitly updated.
 * */
interface KMirror {
    /** Class that will be reflected for accessing the fields. */
    val reflectTargetClass: Class<*>

    /**
     * Object that is used as a target for reflection.
     *
     * If this is `null` delegates work on `thisRef` instead (object which calls the delegate).
     * */
    val reflectTargetObject: Any?
        get() = null

    /** Reflect field cache - can be instance for singletons, or map in an object. */
    val reflectedFieldCache: HashMap<String, Field>

    /** Reflects field from [reflectTargetClass]. */
    operator fun <T> getValue(thisRef: Any, property: KProperty<*>): T =
        reflectGet(reflectTargetObject ?: thisRef, property.name)

    /** Reflects field from [reflectTargetClass]. */
    operator fun <T> setValue(thisRef: Any, property: KProperty<*>, value: T) =
        reflectSet(reflectTargetObject ?: thisRef, property.name, value)

    // companion object acts as a factory
    companion object {
        /**
         * Create default [KMirror] without [reflectTargetObject]. This will reflect objects
         * that host calling delegates.
         * */
        inline fun <reified T> ofClass() = ofClass(T::class.java)

        /**
         * Create default [KMirror] without [reflectTargetObject]. This will reflect objects
         * that host calling delegates.
         * */
        fun <T> ofClass(reflectTargetClass: Class<T>) =
            Default(reflectTargetClass, null)

        /**
         * Create default [KMirror] that will [reflectTargetClass] from [reflectTargetObject].
         *
         * Note this will infer class of [T] - to reflect parent class of [T] specify it explicitly.
         * */
        inline fun <reified T> of(reflectTargetObject: T) = of(T::class.java, reflectTargetObject)

        /** Create default [KMirror] that will [reflectTargetClass] from [reflectTargetObject]. */
        fun <T, R : T> of(reflectTargetClass: Class<R>, reflectTargetObject: T) =
            Default(reflectTargetClass, reflectTargetObject)
    }

    /** Basic implementation of [KMirror]. */
    class Default<out T>(
        override val reflectTargetClass: Class<*>,
        override val reflectTargetObject: T?,
        override val reflectedFieldCache: HashMap<String, Field> = HashMap()
    ) : KMirror

    /**
     *  Delegate for single field used in case where it has special parameters. It can have field [name]
     *  specified explicitly or it can have [inherited] flag risen to find that field in superclasses.
     * */
    class SingleField<T>(private val mirror: KMirror, private val name: String?) : ReadWriteProperty<Any, T> {
        var allowNonDeclared = false
            private set

        /** Allow return of non-declared field (inherited from interface or superclass). */
        fun inherited() = apply { allowNonDeclared = true }

        // internal - unlike default implementation, this caches field
        // using property name instead of field name to prevent clashes
        private fun obtainField(propertyName: String) =
            mirror.reflectedFieldCache.getOrPut(propertyName) {
                getAccessibleField(mirror.reflectTargetClass, name ?: propertyName, allowNonDeclared)
            }

        override fun getValue(thisRef: Any, property: KProperty<*>) =
            obtainField(property.name).get(mirror.reflectTargetObject) as T

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            obtainField(property.name).set(mirror.reflectTargetObject, value)
        }
    }

    /** Delegate for reflection of a method. */
    class SingleMethod<T>(
        val delegateTarget: KMirror,
        val methodName: String?,
        vararg val params: Class<*>
    ) : ReadOnlyProperty<Any, Method<T>> {
        private var mMethod: Method<T>? = null
        override fun getValue(thisRef: Any, property: KProperty<*>): Method<T> {
            if (mMethod == null) {
                mMethod = if (params.isEmpty())
                    delegateTarget.getMethod(methodName ?: property.name)
                else
                    delegateTarget.getMethod(methodName ?: property.name, *params)
            }
            return mMethod!!
        }
    }


    /** [java.lang.reflect.Method] wrapper with custom [invoke]. [T] is type of data returned from [method]. */
    class Method<T>(
        /** Object invoking reflected method. */
        val reflectTargetObject: Any,
        /** Wrapped reflected method. */
        val method: java.lang.reflect.Method
    ) {
        operator fun invoke(vararg args: Any?) = method(reflectTargetObject, *args) as T
    }
}

/** Delegate to access single field of reflected class OR one of its superclasses or interfaces. */
fun <T> KMirror.inherited() =  KMirror.SingleField<T>(this, null)

/** Delegate to access single field of reflected class - use if there's field name collision. */
fun <T> KMirror.field(fieldName: String) = KMirror.SingleField<T>(this, fieldName)

/** Get field from [KMirror.reflectedFieldCache] or perform reflection and make it accessible. */
fun KMirror.getReflectedField(name: String) = reflectedFieldCache.getOrPut(name) {
    getAccessibleField(reflectTargetClass, name)
}

/** Get field with given name from [sourceObject], using field cache of this [KMirror]. */
@Suppress("UNCHECKED_CAST")
fun <T> KMirror.reflectGet(sourceObject: Any, fieldName: String) =
    getReflectedField(fieldName).get(sourceObject) as T

/** Set field of [targetObject] with [fieldName] to value, using field cache of this [KMirror]. */
fun <T> KMirror.reflectSet(targetObject: Any, fieldName: String, value: T) {
    getReflectedField(fieldName).set(targetObject, value)
}

/** Reflect default constructor, make it accessible and create new instance of this object.*/
fun <T : Any> KClass<T>.forceCreateNewInstance(): T =
    this.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

/** Internal - get a raw accessible field without crawling in super classes. */
internal fun getAccessibleField(clazz: Class<*>, name: String) =
    clazz.getDeclaredField(name).apply { isAccessible = true }

/** Internal - get a raw accessible field. If [allowNonDeclared] is true also try to get inherited field. */
internal fun getAccessibleField(clazz: Class<*>, name: String, allowNonDeclared: Boolean) =
    try {
        clazz.getDeclaredField(name)
    } catch (e: NoSuchFieldException) {
        if (allowNonDeclared) clazz.getField(name)
        else throw e
    }.apply { isAccessible = true }

/*
    ----------------- METHOD REFLECTION BELOW ---------------
 */

/**
 * Create delegate for method from [KMirror.reflectTargetClass], using property name only.
 *
 * That method must not return any value (`void` in java, `Unit` in kotlin).
 * */
@JvmName("reflectMethodVoid")
fun KMirror.reflectMethod() = reflectMethod<Unit>()

/**
 * Create delegate for method from [KMirror.reflectTargetClass] using property name only.
 *
 * Method must return values of type [T].
 * */
fun <T> KMirror.reflectMethod() = KMirror.SingleMethod<T>(this, null)

/**
 * Create delegate for method from [KMirror.reflectTargetClass].
 * */
fun <T> KMirror.reflectMethod(methodName: String, vararg params: Class<*>) =
    KMirror.SingleMethod<T>(this, methodName, *params)

/**
 * Create delegate for method from [KMirror.reflectTargetClass]. It will use property name
 * to determine method name.
 * */
fun <T> KMirror.reflectMethod(vararg params: Class<*>) =
    KMirror.SingleMethod<T>(this, null, *params)

/**
 * Reflect method from [KMirror.reflectTargetClass] using name only (no cache for methods).
 *
 * *(use only if method has no overloads so it's not ambiguous)*.
 **/
fun <T> KMirror.getMethod(methodName: String): KMirror.Method<T> {
    val m = reflectTargetClass.declaredMethods.find { it.name == methodName }!!.apply {
        isAccessible = true
    }
    return KMirror.Method(reflectTargetObject ?: this, m)
}

/** Reflect method from [KMirror.reflectTargetClass] without cache. */
fun <T> KMirror.getMethod(methodName: String, vararg params: Class<*>): KMirror.Method<T> {
    val m = reflectTargetClass.getDeclaredMethod(methodName, *params).apply { isAccessible = true }
    return KMirror.Method(reflectTargetObject ?: this, m)
}

/// reflect by using invoke operator - disabled (too confusing)


///**
// * Create delegate for method from [KMirror.reflectTargetClass], using property name only.
// *
// * Return value will not be cast.
// * */
//@JvmName("invokeAny")
//operator fun KMirror.invoke() = reflectMethod()
//
///**
// * Create delegate for method from [KMirror.reflectTargetClass], using property name only.
// *
// * Method must return values of type [T].
// * */
//operator fun <T> KMirror.invoke() = reflectMethod<T>()
//
///**
// * Create delegate for method from [KMirror.reflectTargetClass].
// * */
//operator fun <T> KMirror.invoke(methodName: String, vararg params: Class<*>) =
//    reflectMethod<T>(methodName, *params)
//
///**
// * Create delegate for method from [KMirror.reflectTargetClass]. It will use property name
// * to determine method name.
// * */
//operator fun <T> KMirror.invoke(vararg params: Class<*>) = reflectMethod<T>(*params)


