@file:Suppress("unused")

package com.github.ppaszkiewicz.tools.demo

import com.github.ppaszkiewicz.tools.toolbox.reflection.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Self contained [KMirror] test.
 * */
@RunWith(JUnit4::class)
class IReflectorTest {
    @Test
    fun privateContainerMirrorTest() {
        PrivateContainerMirror().apply {
            println(str)
            //modify reflected value
            str = "Not so hidden now are we"
            println(str)
            println(list.joinToString())
            // invoke a reflected method
            val x: Any? = noReturn()
            println(x) // should print Unit
            println(method())
            println(overload("uppercase me"))
            println(overloadInt(2, 5f))
            // need to use JVM name obviously - peek function name
            println(listOverloadString.method)
            // sort
            println(listOverloadInt(list).joinToString())
        }
    }

    @Test
    fun privateObjectMirrorTest(){
        // object that hides values from us
        val privObject = PrivateObject::class.forceCreateNewInstance()

        // reflects field from another object by implementing delegate
        PrivateObjectReflector(privObject).apply {
            println(double)
            double = 15.0    // can modify values (ignores VAL declaration)
            println(double)
            println(privateMethod())
        }

        val privObject2 = PrivateObjectChild::class.forceCreateNewInstance()

        // reflects field form another object without implementing IFieldReflector interface itself
        NotReflector(privObject, privObject2).apply {
            println(double)
            val privReturn = privateMethod()
            println(privReturn + ", string length: ${privReturn.length}")
            println(privReturn.javaClass)
            println(privateMethod2())
            println("pub field is: $publicField")
            publicField += 5
            println("pub field is: $publicField (modified)")
            assert(publicField == privObject.publicField)
//            println("pub field2 is: $publicField2")
//            publicField2 += 10
//            println("pub field2 is: $publicField2 (modified)")
//            assert(publicField2 == privObject2.publicField)
        }
    }
}


/** Open class with private fields and methods that will be reflected. */
@Suppress("unused")
open class PrivateContainer {
    private var str: String = "Hidden value in PrivateContainer"
    private val list: List<Int> = listOf(1, 4, 2, 0, 5, 8)

    private fun noReturn() {
        println("noReturn invoked in $this")
    }

    private fun method(): String = "Hidden method invoked"

    private fun overload(str: String) = str.toUpperCase()
    private fun overload(i: Int, f: Float) = i * f

    @JvmName("listOverloadString")
    private fun listOverload(l: List<String>) = l.joinToString(separator = ":")

    @JvmName("listOverloadInt")
    private fun listOverload(l: List<Int>) = l.sorted()
}

/** Another class - not open so it cannot be inherited from. Also hides it's constructor. */
open class PrivateObject protected constructor() {
    private val double: Double = 22.22
    private fun privateMethod() = "Im hidden in PrivateObject"
    var publicField : Int = 55
        private set
}

class PrivateObjectChild : PrivateObject(){
    private fun privateMethod() = "Im hidden in PrivateObjectChild"
}

/** Reflects from SELF - [KMirror] delegate without object argument (only specifies class to reflect). */
class PrivateContainerMirror : PrivateContainer(), KMirror by KMirror.ofClass<PrivateContainer>() {
    /** Reflects str from self.*/
    var str: String by this
    val list: List<Int> by this

    // lazy method reflections
    // there are three ways of calling method reflection: this(), invoke() or reflectMethod()
    // all of them act the same way but might prove to be more readable depending on context
    val noReturn by reflectMethod()
    val method by reflectMethod()
    val overload by reflectMethod<String>(String::class.java)  // can also use invoke operator name

    // can't declare another "overload" field, have to specify method name explicitly because it doesn't match
    val overloadInt by reflectMethod<Float>("overload", Int::class.java, Float::class.java)

    // reflected methods must use JvmName if needed
    val listOverloadString by reflectMethod<String>()
    val listOverloadInt by reflectMethod<List<Int>>()
}


/** Reflects fields from class that cannot be extended - [KMirror] delegate with object argument. */
open class PrivateObjectReflector(val privateObject: PrivateObject) :
    KMirror by KMirror.of(privateObject) {
    var double: Double by this
    val privateMethod by reflectMethod()
}

/** Reflects fields from multiple objects without implementing [KMirror] itself. */
class NotReflector(privateObject: PrivateObject, privateObject2: PrivateObject) {
    private val mirror = KMirror.of(privateObject)
    val double: Double by mirror
    val privateMethod by mirror.reflectMethod<String>()
    var publicField : Int by mirror.inherited()

    private val mirror2 = KMirror.of(PrivateObjectChild::class.java, privateObject2)

    val privateMethod2 by mirror2.reflectMethod<String>("privateMethod")
    // work around field name collision specifying field name as arg, and raise inherited flag
    //var publicField2 by mirror2.field<Int>("publicField").inherited()     doesn't work (?)
    //val double2: Double by mirror2.field("double") this will be inaccessible because child is reflected
}