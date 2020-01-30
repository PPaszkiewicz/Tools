@file:Suppress("unused")

package com.github.ppaszkiewicz.tools.demo

import com.github.ppaszkiewicz.tools.toolbox.reflection.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Self contained [IFieldReflector] test.
 * */
@RunWith(JUnit4::class)
class IReflectorTest {
    @Test
    fun helloTest() {
        PrivateContainerReflector().apply {
            println(str)
            //modify reflected value
            str = "Not so hidden now are we"
            println(str)
            println(list.joinToString())
            // invoke a reflected method
            val x: Any = noReturn()
            println(x) // should print Unit
            println(method())
            println(overload("uppercase me"))
            println(overloadInt(2, 5f))
            // need to use JVM name obviously - peek function name
            println(listOverloadString.method)
            // sort
            println(listOverloadInt(list).joinToString())
        }

        // object that hides values from us
        val privObject = PrivateObject::class.newInstance()

        // reflects field from another object by implementing delegate
        PrivateObjectReflector(privObject).apply {
            println(double)
            double = 15.0    // can modify values (ignores VAL declaration)
            println(double)
            println(privateMethod())
        }

        val privObject2 = PrivateObject::class.newInstance()

        // reflects field form another object without implementing IFieldReflector interface itself
        NotReflector(privObject, privObject2).apply {
            println(double)
            println(double2)
            val privReturn = privateMethod()
            println(privReturn + ", string length: ${privReturn.length}")
            println(privReturn.javaClass)
            println(privateMethod2())
        }
    }
}


/** Open class with private fields and methods that will be reflected. */
@Suppress("unused")
open class PrivateContainer {
    private var str: String = "Hidden value"
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
class PrivateObject private constructor() {
    private val double: Double = 22.22
    fun privateMethod() = "Im hidden good"
}

/** Reflects from SELF - [IFieldReflector] delegate with null object argument (only specifies class to reflect). */
class PrivateContainerReflector : PrivateContainer(),
    IFieldReflector by FieldReflector<PrivateContainer>() {
    /** Reflects str from self.*/
    var str: String by this
    val list: List<Int> by this

    // lazy method reflections
    // there are three ways of calling method reflection: this(), invoke() or reflectMethod()
    // all of them act the same way but might prove to be more readable depending on context
    val noReturn by this()
    val method by reflectMethod()  // acts the same as this()
    val overload by invoke<String>(String::class.java)  // can also use invoke operator name

    // can't declare another "overload" field, have to specify method name explicitly because it doesn't match
    val overloadInt by this<Float>("overload", Int::class.java, Float::class.java)

    // reflected methods must use JvmName if needed
    val listOverloadString by reflectMethod<String>()
    val listOverloadInt by this<List<Int>>()
}

/** Reflects fields from class that cannot be extended - [IFieldReflector] delegate with argument. */
class PrivateObjectReflector(val privateObject: PrivateObject) :
    IFieldReflector by FieldReflector(privateObject) {
    var double: Double by this
    val privateMethod by invoke()
}

/** Reflects fields from multiple objects without implementing [IFieldReflector] itself. */
class NotReflector(privateObject: PrivateObject, privateObject2: PrivateObject) {
    private val reflector = FieldReflector(privateObject)
    val double: Double by reflector
    val privateMethod by reflector<String>()

    private val reflector2 = FieldReflector(privateObject2)
    // work around field name collision
    val double2: Double by reflector2.field("double")
    val privateMethod2 by reflector2<String>("privateMethod")
}