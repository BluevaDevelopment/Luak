/******************************************************************************
 *  _                _
 * | |   _   _  __ _| | __
 * | |  | | | |/ _` | |/ /
 * | |__| |_| | (_| |   <
 * |_____\__,_|\__,_|_|\_\
 *
 *  Luak
 *  https://github.com/BluevaDevelopment/Luak
 *
 *  Based on LuaJ (https://luaj.org)
 *  Original work Copyright (c) 2009 Luaj.org
 *  Modifications Copyright (c) 2026 Blueva Development
 *
 *  SPDX-License-Identifier: MIT
 ******************************************************************************/
package net.blueva.luak.script

import junit.framework.TestCase
import junit.framework.TestSuite
import net.blueva.luak.Lua
import net.blueva.luak.LuaFunction
import net.blueva.luak.LuaValue
import net.blueva.luak.lib.OneArgFunction
import net.blueva.luak.script.LuaScriptEngine.LuaCompiledScript
import java.io.CharArrayReader
import java.io.CharArrayWriter
import java.io.Reader
import javax.script.*

object ScriptEngineTests : TestSuite() {
    fun suite(): TestSuite {
        val suite = TestSuite("Script Engine Tests")
        suite.addTest(TestSuite(LookupEngineTestCase::class.java, "Lookup Engine"))
        suite.addTest(TestSuite(DefaultBindingsTest::class.java, "Default Bindings"))
        suite.addTest(TestSuite(SimpleBindingsTest::class.java, "Simple Bindings"))
        suite.addTest(TestSuite(CompileClosureTest::class.java, "Compile Closure"))
        suite.addTest(TestSuite(CompileNonClosureTest::class.java, "Compile NonClosure"))
        suite.addTest(TestSuite(UserContextTest::class.java, "User Context"))
        suite.addTest(TestSuite(WriterTest::class.java, "Writer"))
        return suite
    }

    class LookupEngineTestCase : TestCase() {
        fun testGetEngineByExtension() {
            val e = ScriptEngineManager().getEngineByExtension(".lua")
            assertNotNull(e)
            assertEquals(LuaScriptEngine::class.java, e!!.javaClass)
        }

        fun testGetEngineByName() {
            val e = ScriptEngineManager().getEngineByName("luaj")
            assertNotNull(e)
            assertEquals(LuaScriptEngine::class.java, e.javaClass)
        }

        fun testGetEngineByMimeType() {
            val e = ScriptEngineManager().getEngineByMimeType("text/lua")
            assertNotNull(e)
            assertEquals(LuaScriptEngine::class.java, e!!.javaClass)
        }

        fun testFactoryMetadata() {
            val e = ScriptEngineManager().getEngineByName("luaj")
            val f = e.getFactory()
            TestCase.assertEquals("Luak", f.getEngineName())
            TestCase.assertEquals(Lua.LUAK_VERSION, f.getEngineVersion())
            TestCase.assertEquals("lua", f.getLanguageName())
            TestCase.assertEquals(Lua._VERSION.removePrefix("Lua "), f.getLanguageVersion())
        }
    }

    open class DefaultBindingsTest : EngineTestCase() {
        override fun createBindings(): Bindings {
            return e!!.createBindings()
        }
    }

    class SimpleBindingsTest : EngineTestCase() {
        override fun createBindings(): Bindings {
            return SimpleBindings()
        }
    }

    class CompileClosureTest : DefaultBindingsTest() {
        @Throws(Exception::class)
        override fun setUp() {
            System.setProperty("org.luaj.luajc", "false")
            super.setUp()
        }

        @Throws(ScriptException::class)
        fun testCompiledFunctionIsClosure() {
            val cs = (e as Compilable).compile("return 'foo'")
            val value: LuaValue = (cs as LuaCompiledScript).function
            assertTrue(value.isclosure())
        }
    }

    class CompileNonClosureTest : DefaultBindingsTest() {
        @Throws(Exception::class)
        override fun setUp() {
            System.setProperty("org.luaj.luajc", "true")
            super.setUp()
        }

        @Throws(ScriptException::class)
        fun testCompiledFunctionIsNotClosure() {
            val cs = (e as Compilable).compile("return 'foo'")
            val value: LuaValue = (cs as LuaCompiledScript).function
            assertFalse(value.isclosure())
        }
    }

    abstract class EngineTestCase : TestCase() {
        protected var e: ScriptEngine? = null
        protected var b: Bindings? = null
        protected abstract fun createBindings(): Bindings

        @Throws(Exception::class)
        override fun setUp() {
            this.e = ScriptEngineManager().getEngineByName("luaj")
            this.b = createBindings()
        }

        @Throws(ScriptException::class)
        fun testSqrtFloatResult() {
            // math.sqrt is a float function: its result is 5.0, not 5, and
            // crosses into Java as a Double.
            e!!.put("x", 25)
            e!!.eval("y = math.sqrt(x)")
            val y = e!!.get("y")
            assertEquals(5.0, y)
        }

        @Throws(ScriptException::class)
        fun testOneArgFunction() {
            e!!.put("x", 25)
            e!!.eval("y = math.sqrt(x)")
            val y = e!!.get("y")
            assertEquals(5.0, y)
            e!!.put("f", object : OneArgFunction() {
                override fun call(arg: LuaValue?): LuaValue {
                    return valueOf(arg!!.toString() + "123")
                }
            })
            val r = e!!.eval("return f('abc')")
            assertEquals("abc123", r)
        }

        @Throws(ScriptException::class)
        fun testCompiledScript() {
            val cs = (e as Compilable).compile("y = math.sqrt(x); return y")
            b!!.put("x", 144)
            assertEquals(12.0, cs.eval(b))
        }

        fun testBuggyLuaScript() {
            try {
                e!!.eval("\n\nbuggy lua code\n\n")
            } catch (se: ScriptException) {
                TestCase.assertEquals(
                    // The message names the token the compiler stopped at.
                    "eval threw javax.script.ScriptException: " +
                        "[string \"script\"]:3: syntax error near 'lua'",
                    se.message
                )
                return
            }
            fail("buggy script did not throw ScriptException as expected.")
        }

        @Throws(ScriptException::class)
        fun testScriptRedirection() {
            val input: Reader = CharArrayReader("abcdefg\nhijk".toCharArray())
            val output = CharArrayWriter()
            val errors = CharArrayWriter()
            val script =
                "print(\"string written using 'print'\")\n" +
                        "io.write(\"string written using 'io.write()'\\n\")\n" +
                        "io.stdout:write(\"string written using 'io.stdout:write()'\\n\")\n" +
                        "io.stderr:write(\"string written using 'io.stderr:write()'\\n\")\n" +
                        "io.write([[string read using 'io.stdin:read(\"*l\")':]]..io.stdin:read(\"*l\")..\"\\n\")\n"

            // Evaluate script with redirection set
            e!!.getContext().setReader(input)
            e!!.getContext().setWriter(output)
            e!!.getContext().setErrorWriter(errors)
            e!!.eval(script)
            val expectedOutput = "string written using 'print'\n" +
                    "string written using 'io.write()'\n" +
                    "string written using 'io.stdout:write()'\n" +
                    "string read using 'io.stdin:read(\"*l\")':abcdefg\n"
            TestCase.assertEquals(expectedOutput, output.toString())
            val expectedErrors = "string written using 'io.stderr:write()'\n"
            TestCase.assertEquals(expectedErrors, errors.toString())

            // Evaluate script with redirection reset
            output.reset()
            errors.reset()
            // e.getContext().setReader(null); // This will block if using actual STDIN
            e!!.getContext().setWriter(null)
            e!!.getContext().setErrorWriter(null)
            e!!.eval(script)
            TestCase.assertEquals("", output.toString())
            TestCase.assertEquals("", errors.toString())
        }

        @Throws(ScriptException::class)
        fun testBindingJavaInt() {
            val cs = (e as Compilable).compile("y = x; return 'x '..type(x)..' '..tostring(x)\n")
            b!!.put("x", 111)
            assertEquals("x number 111", cs.eval(b))
            assertEquals(111, b!!.get("y"))
        }

        @Throws(ScriptException::class)
        fun testBindingJavaDouble() {
            val cs = (e as Compilable).compile("y = x; return 'x '..type(x)..' '..tostring(x)\n")
            b!!.put("x", 125.125)
            assertEquals("x number 125.125", cs.eval(b))
            assertEquals(125.125, b!!.get("y"))
        }

        @Throws(ScriptException::class)
        fun testBindingJavaString() {
            val cs = (e as Compilable).compile("y = x; return 'x '..type(x)..' '..tostring(x)\n")
            b!!.put("x", "foo")
            assertEquals("x string foo", cs.eval(b))
            assertEquals("foo", b!!.get("y"))
        }

        @Throws(ScriptException::class)
        fun testBindingJavaObject() {
            val cs = (e as Compilable).compile("y = x; return 'x '..type(x)..' '..tostring(x)\n")
            b!!.put("x", SomeUserClass())
            assertEquals("x userdata some-user-value", cs.eval(b))
            assertEquals(SomeUserClass::class.java, b!!.get("y")!!.javaClass)
        }

        @Throws(ScriptException::class)
        fun testBindingJavaArray() {
            val cs = (e as Compilable).compile("y = x; return 'x '..type(x)..' '..#x..' '..x[1]..' '..x[2]\n")
            b!!.put("x", intArrayOf(777, 888))
            assertEquals("x userdata 2 777 888", cs.eval(b))
            assertEquals(IntArray::class.java, b!!.get("y")!!.javaClass)
        }

        @Throws(ScriptException::class)
        fun testBindingLuaFunction() {
            val cs = (e as Compilable).compile("y = function(x) return 678 + x end; return 'foo'")
            TestCase.assertEquals("foo", cs.eval(b).toString())
            assertTrue(b!!.get("y") is LuaFunction)
            assertEquals(LuaValue.valueOf(801), (b!!.get("y") as LuaFunction).call(LuaValue.valueOf(123)))
        }

        @Throws(ScriptException::class)
        fun testUserClasses() {
            val cs = (e as Compilable).compile(
                "x = x or luajava.newInstance('java.lang.String', 'test')\n" +
                        "return 'x ' ..  type(x) .. ' ' .. tostring(x)\n"
            )
            assertEquals("x string test", cs.eval(b))
            b!!.put("x", SomeUserClass())
            assertEquals("x userdata some-user-value", cs.eval(b))
        }

        @Throws(ScriptException::class)
        fun testReturnMultipleValues() {
            val cs = (e as Compilable).compile("return 'foo', 'bar'\n")
            val o = cs.eval()
            assertEquals(Array<Any>::class.java, o.javaClass)
            val array = o as Array<Any?>
            TestCase.assertEquals(2, array.size)
            assertEquals("foo", array[0])
            assertEquals("bar", array[1])
        }
    }

    class SomeUserClass {
        override fun toString(): String {
            return "some-user-value"
        }
    }

    class UserContextTest : TestCase() {
        protected var e: ScriptEngine? = null
        protected var b: Bindings? = null
        protected var c: ScriptContext? = null
        public override fun setUp() {
            this.e = ScriptEngineManager().getEngineByName("luaj")
            this.c = LuaContext()
            this.b = c!!.getBindings(ScriptContext.ENGINE_SCOPE)
        }

        @Throws(ScriptException::class)
        fun testUncompiledScript() {
            b!!.put("x", 144)
            assertEquals(12.0, e!!.eval("z = math.sqrt(x); return z", b))
            assertEquals(12.0, b!!.get("z"))
            assertEquals(null, e!!.getBindings(ScriptContext.ENGINE_SCOPE).get("z"))
            assertEquals(null, e!!.getBindings(ScriptContext.GLOBAL_SCOPE).get("z"))

            b!!.put("x", 25)
            assertEquals(5.0, e!!.eval("z = math.sqrt(x); return z", c))
            assertEquals(5.0, b!!.get("z"))
            assertEquals(null, e!!.getBindings(ScriptContext.ENGINE_SCOPE).get("z"))
            assertEquals(null, e!!.getBindings(ScriptContext.GLOBAL_SCOPE).get("z"))
        }

        @Throws(ScriptException::class)
        fun testCompiledScript() {
            val cs = (e as Compilable).compile("z = math.sqrt(x); return z")

            b!!.put("x", 144)
            assertEquals(12.0, cs.eval(b))
            assertEquals(12.0, b!!.get("z"))

            b!!.put("x", 25)
            assertEquals(5.0, cs.eval(c))
            assertEquals(5.0, b!!.get("z"))
        }
    }

    class WriterTest : TestCase() {
        protected var e: ScriptEngine? = null
        protected var b: Bindings? = null
        public override fun setUp() {
            this.e = ScriptEngineManager().getEngineByName("luaj")
            this.b = e!!.getBindings(ScriptContext.ENGINE_SCOPE)
        }

        @Throws(ScriptException::class)
        fun testWriter() {
            val output = CharArrayWriter()
            val errors = CharArrayWriter()
            e!!.getContext().setWriter(output)
            e!!.getContext().setErrorWriter(errors)
            e!!.eval("io.write( [[line]] )")
            TestCase.assertEquals("line", output.toString())
            e!!.eval("io.write( [[ one\nline two\n]] )")
            TestCase.assertEquals("line one\nline two\n", output.toString())
            output.reset()
        }
    }
}
