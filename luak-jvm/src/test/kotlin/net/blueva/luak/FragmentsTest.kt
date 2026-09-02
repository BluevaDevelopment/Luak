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
package net.blueva.luak

import net.blueva.luak.lib.jvm.asLuaReader
import junit.framework.TestCase
import junit.framework.TestSuite
import net.blueva.luak.lib.jvm.JvmPlatform.debugGlobals
import net.blueva.luak.luajc.LuaJC.Companion.install
import java.io.Reader
import java.io.StringReader

/**
 * Test compilation of various fragments that have
 * caused problems for jit compiling during development.
 * 
 */
object FragmentsTest : TestSuite() {
    const val TEST_TYPE_LUAC: Int = 0
    const val TEST_TYPE_LUAJC: Int = 1

    fun suite(): TestSuite {
        val suite = TestSuite("Compiler Fragments Tests")
        suite.addTest(TestSuite(JvmFragmentsTest::class.java, "JVM Fragments Tests"))
        suite.addTest(TestSuite(LuaJCFragmentsTest::class.java, "LuaJC Fragments Tests"))
        return suite
    }

    class JvmFragmentsTest : FragmentsTestCase(TEST_TYPE_LUAC)
    class LuaJCFragmentsTest : FragmentsTestCase(TEST_TYPE_LUAJC)
    abstract class FragmentsTestCase constructor(val TEST_TYPE: Int) : TestCase() {
        fun runFragment(expected: Varargs, script: String) {
            try {
                val name = getName()
                val globals = debugGlobals()
                val reader: Reader = StringReader(script)
                val chunk: LuaValue
                when (TEST_TYPE) {
                    TEST_TYPE_LUAJC -> {
                        install(globals)
                        chunk = globals.load(reader.asLuaReader(), name)!!
                    }

                    else -> {
                        val p = globals.compilePrototype(reader.asLuaReader(), name)
                        chunk = LuaClosure(p!!, globals)
                        Print.print(p)
                    }
                }
                val actual: Varargs = chunk.invoke()!!
                TestCase.assertEquals(expected.narg(), actual.narg())
                for (i in 1..actual.narg()) assertEquals(expected.arg(i), actual.arg(i))
            } catch (e: Exception) {
                // TODO Auto-generated catch block
                e.printStackTrace()
                fail(e.toString())
            }
        }

        fun testFirstArgNilExtended() {
            runFragment(
                LuaValue.NIL,
                "function f1(a) print( 'f1:', a ) return a end\n" +
                        "b = f1()\n" +
                        "return b"
            )
        }

        fun testSimpleForloop() {
            runFragment(
                LuaValue.valueOf(77),
                "for n,p in ipairs({77}) do\n" +
                        "	print('n,p',n,p)\n" +
                        "   return p\n" +
                        "end\n"
            )
        }

        fun testForloopParamUpvalues() {
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        net.blueva.luak.LuaValue.valueOf(77),
                        net.blueva.luak.LuaValue.valueOf(1)
                    )
                )!!,
                "for n,p in ipairs({77}) do\n" +
                        "	print('n,p',n,p)\n" +
                        "	foo = function()\n" +
                        "		return p,n\n" +
                        "	end\n" +
                        "	return foo()\n" +
                        "end\n"
            )
        }

        fun testArgVarargsUseBoth() {
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue>(
                        net.blueva.luak.LuaValue.valueOf("a"),
                        net.blueva.luak.LuaValue.valueOf("b"),
                        net.blueva.luak.LuaValue.valueOf("c")
                    )
                )!!,
                "function v(arg,...)\n" +
                        "	return arg,...\n" +
                        "end\n" +
                        "return v('a','b','c')\n"
            )
        }

        fun testArgParamUseNone() {
            runFragment(
                LuaValue.valueOf("string"),
                "function v(arg,...)\n" +
                        "	return type(arg)\n" +
                        "end\n" +
                        "return v('abc')\n"
            )
        }

        fun testSetlistVarargs() {
            runFragment(
                LuaValue.valueOf("abc"),
                "local f = function() return 'abc' end\n" +
                        "local g = { f() }\n" +
                        "return g[1]\n"
            )
        }

        fun testSelfOp() {
            runFragment(
                LuaValue.valueOf("bcd"),
                "local s = 'abcde'\n" +
                        "return s:sub(2,4)\n"
            )
        }

        fun testSetListWithOffsetAndVarargs() {
            runFragment(
                // math.sqrt is a float function, so the sum is a float too.
                LuaValue.valueOf(1003.0),
                "local bar = {1000, math.sqrt(9)}\n" +
                        "return bar[1]+bar[2]\n"
            )
        }

        fun testMultiAssign() {
            // arargs evaluations are all done before assignments 
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        net.blueva.luak.LuaValue.valueOf(111),
                        net.blueva.luak.LuaValue.valueOf(111),
                        net.blueva.luak.LuaValue.valueOf(111)
                    )
                )!!,
                "a,b,c = 1,10,100\n" +
                        "a,b,c = a+b+c, a+b+c, a+b+c\n" +
                        "return a,b,c\n"
            )
        }

        fun testUpvalues() {
            runFragment(
                LuaValue.valueOf(999),
                "local a = function(x)\n" +
                        "  return function(y)\n" +
                        "    return x + y\n" +
                        "  end\n" +
                        "end\n" +
                        "local b = a(222)\n" +
                        "local c = b(777)\n" +
                        "print( 'c=', c )\n" +
                        "return c\n"
            )
        }

        fun testNonAsciiStringLiterals() {
            runFragment(
                LuaValue.valueOf("7,8,12,10,9,11,133,222"),
                "local a='\\a\\b\\f\\n\\t\\v\\133\\222'\n" +
                        "local t={string.byte(a,1,#a)}\n" +
                        "return table.concat(t,',')\n"
            )
        }

        fun testControlCharStringLiterals() {
            runFragment(
                LuaValue.valueOf("97,0,98,18,99,18,100,18,48,101"),
                "local a='a\\0b\\18c\\018d\\0180e'\n" +
                        "local t={string.byte(a,1,#a)}\n" +
                        "return table.concat(t,',')\n"
            )
        }

        fun testLoopVarNames() {
            runFragment(
                LuaValue.valueOf(" 234,1,aa 234,2,bb"),
                "local w = ''\n" +
                        "function t()\n" +
                        "	for f,var in ipairs({'aa','bb'}) do\n" +
                        "		local s = 234\n" +
                        "		w = w..' '..s..','..f..','..var\n" +
                        "	end\n" +
                        "end\n" +
                        "t()\n" +
                        "return w\n"
            )
        }

        fun testForLoops() {
            runFragment(
                LuaValue.valueOf("12345 357 963"),
                "local s,t,u = '','',''\n" +
                        "for m=1,5 do\n" +
                        "	s = s..m\n" +
                        "end\n" +
                        "for m=3,7,2 do\n" +
                        "	t = t..m\n" +
                        "end\n" +
                        "for m=9,3,-3 do\n" +
                        "	u = u..m\n" +
                        "end\n" +
                        "return s..' '..t..' '..u\n"
            )
        }

        fun testLocalFunctionDeclarations() {
            runFragment(
                LuaValue.varargsOf(
                    net.blueva.luak.LuaValue.valueOf("function"),
                    net.blueva.luak.LuaValue.valueOf("nil")
                )!!,
                "local function aaa()\n" +
                        "	return type(aaa)\n" +
                        "end\n" +
                        "local bbb = function()\n" +
                        "	return type(bbb)\n" +
                        "end\n" +
                        "return aaa(),bbb()\n"
            )
        }

        fun testNilsInTableConstructor() {
            runFragment(
                LuaValue.valueOf("1=111 2=222 3=333 "),
                "local t = { 111, 222, 333, nil, nil }\n" +
                        "local s = ''\n" +
                        "for i,v in ipairs(t) do \n" +
                        "	s=s..tostring(i)..'='..tostring(v)..' '\n" +
                        "end\n" +
                        "return s\n"
            )
        }

        fun testUnreachableCode() {
            runFragment(
                LuaValue.valueOf(66),
                "local function foo(x) return x * 2 end\n" +
                        "local function bar(x, y)\n" +
                        "	if x==y then\n" +
                        "		return y\n" +
                        "	else\n" +
                        "		return foo(x)\n" +
                        "	end\n" +
                        "end\n" +
                        "return bar(33,44)\n"
            )
        }

        fun testVarargsWithParameters() {
            runFragment(
                LuaValue.valueOf(222),
                "local func = function(t,...)\n" +
                        "	return (...)\n" +
                        "end\n" +
                        "return func(111,222,333)\n"
            )
        }

        fun testNoReturnValuesPlainCall() {
            runFragment(
                LuaValue.TRUE!!,
                "local testtable = {}\n" +
                        "return pcall( function() testtable[1]=2 end )\n"
            )
        }

        fun testVarargsInTableConstructor() {
            runFragment(
                LuaValue.valueOf(222),
                "local function foo() return 111,222,333 end\n" +
                        "local t = {'a','b',c='c',foo()}\n" +
                        "return t[4]\n"
            )
        }

        fun testVarargsInFirstArg() {
            runFragment(
                LuaValue.valueOf(123),
                "function aaa(x) return x end\n" +
                        "function bbb(y) return y end\n" +
                        "function ccc(z) return z end\n" +
                        "return ccc( aaa(bbb(123)), aaa(456) )\n"
            )
        }

        fun testSetUpvalueTableInitializer() {
            runFragment(
                LuaValue.valueOf("b"),
                "local aliases = {a='b'}\n" +
                        "local foo = function()\n" +
                        "	return aliases\n" +
                        "end\n" +
                        "return foo().a\n"
            )
        }


        fun testLoadNilUpvalue() {
            runFragment(
                LuaValue.NIL,
                "tostring = function() end\n" +
                        "local pc \n" +
                        "local pcall = function(...)\n" +
                        "	pc(...)\n" +
                        "end\n" +
                        "return NIL\n"
            )
        }

        fun testUpvalueClosure() {
            runFragment(
                LuaValue.NIL,
                "print()\n" +
                        "local function f2() end\n" +
                        "local function f3()\n" +
                        "	return f3\n" +
                        "end\n" +
                        "return NIL\n"
            )
        }

        fun testUninitializedUpvalue() {
            runFragment(
                LuaValue.NIL,
                "local f\n" +
                        "do\n" +
                        "	function g()\n" +
                        "		print(f())\n" +
                        "	end\n" +
                        "end\n" +
                        "return NIL\n"
            )
        }

        fun testTestOpUpvalues() {
            runFragment(
                LuaValue.varargsOf(LuaValue.valueOf(1), LuaValue.valueOf(2), LuaValue.valueOf(3)),
                "print( nil and 'T' or 'F' )\n" +
                        "local a,b,c = 1,2,3\n" +
                        "function foo()\n" +
                        "	return a,b,c\n" +
                        "end\n" +
                        "return foo()\n"
            )
        }

        fun testTestSimpleBinops() {
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        net.blueva.luak.LuaValue.FALSE,
                        net.blueva.luak.LuaValue.FALSE,
                        net.blueva.luak.LuaValue.TRUE,
                        net.blueva.luak.LuaValue.TRUE,
                        net.blueva.luak.LuaValue.FALSE
                    )
                )!!,
                "local a,b,c = 2,-2.5,0\n" +
                        "return (a==c), (b==c), (a==a), (a>c), (b>0)\n"
            )
        }

        /**
         * A loop variable captured by a closure inside the loop.
         *
         * The captured value is a copy: since Lua 5.5 the loop's own variable
         * is a constant and cannot be assigned to.
         */
        fun testNumericForUpvalues() {
            runFragment(
                LuaValue.valueOf(8),
                "for i = 3,4 do\n" +
                        "	local j = i + 5\n" +
                        "	local a = function()\n" +
                        "		return j\n" +
                        "	end\n" +
                        "	return a()\n" +
                        "end\n"
            )
        }

        fun testNumericForUpvalues2() {
            runFragment(
                LuaValue.valueOf("222 222"),
                "local t = {}\n" +
                        "local template = [[123 456]]\n" +
                        "for i = 1,2 do\n" +
                        "	t[i] = template:gsub('%d', function(s)\n" +
                        "		return i\n" +
                        "	end)\n" +
                        "end\n" +
                        "return t[2]\n"
            )
        }

        fun testReturnUpvalue() {
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        net.blueva.luak.LuaValue.ONE,
                        net.blueva.luak.LuaValue.valueOf(5),
                    )
                )!!,
                "local a = 1\n" +
                        "local b\n" +
                        "function c()\n" +
                        "	b=5\n" +
                        "	return a\n" +
                        "end\n" +
                        "return c(),b\n"
            )
        }

        fun testUninitializedAroundBranch() {
            runFragment(
                LuaValue.valueOf(333),
                "local state\n" +
                        "if _G then\n" +
                        "    state = 333\n" +
                        "end\n" +
                        "return state\n"
            )
        }

        fun testLoadedNilUpvalue() {
            runFragment(
                LuaValue.NIL,
                "local a = print()\n" +
                        "local b = c and { d = e }\n" +
                        "local f\n" +
                        "local function g()\n" +
                        "	return f\n" +
                        "end\n" +
                        "return g()\n"
            )
        }

        fun testUpvalueInFirstSlot() {
            runFragment(
                LuaValue.valueOf("foo"),
                "local p = {'foo'}\n" +
                        "bar = function()\n" +
                        "	return p \n" +
                        "end\n" +
                        "for i,key in ipairs(p) do\n" +
                        "	print()\n" +
                        "end\n" +
                        "return bar()[1]"
            )
        }

        fun testReadOnlyAndReadWriteUpvalues() {
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        net.blueva.luak.LuaValue.valueOf(333),
                        net.blueva.luak.LuaValue.valueOf(222)
                    )
                )!!,
                "local a = 111\n" +
                        "local b = 222\n" +
                        "local c = function()\n" +
                        "	a = a + b\n" +
                        "	return a,b\n" +
                        "end\n" +
                        "return c()\n"
            )
        }

        fun testNestedUpvalues() {
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        net.blueva.luak.LuaValue.valueOf(5),
                        net.blueva.luak.LuaValue.valueOf(8),
                        net.blueva.luak.LuaValue.valueOf(9)
                    )
                )!!,
                "local x = 3\n" +
                        "local y = 5\n" +
                        "local function f()\n" +
                        "   return y\n" +
                        "end\n" +
                        "local function g(x1, y1)\n" +
                        "   x = x1\n" +
                        "   y = y1\n" +
                        "	return x,y\n" +
                        "end\n" +
                        "return f(), g(8,9)\n" +
                        "\n"
            )
        }

        fun testLoadBool() {
            runFragment(
                LuaValue.NONE!!,
                "print( type(foo)=='string' )\n" +
                        "local a,b\n" +
                        "if print() then\n" +
                        "	b = function()\n" +
                        "		return a\n" +
                        "	end\n" +
                        "end\n"
            )
        }

        fun testBasicForLoop() {
            runFragment(
                LuaValue.valueOf(2),
                "local data\n" +
                        "for i = 1, 2 do\n" +
                        "     data = i\n" +
                        "end\n" +
                        "local bar = function()\n" +
                        "	return data\n" +
                        "end\n" +
                        "return bar()\n"
            )
        }

        fun testGenericForMultipleValues() {
            runFragment(
                LuaValue.varargsOf(LuaValue.valueOf(3), LuaValue.valueOf(2), LuaValue.valueOf(1)),
                "local iter = function() return 1,2,3,4 end\n" +
                        "local foo  = function() return iter,5 end\n" +
                        "for a,b,c in foo() do\n" +
                        "    return c,b,a\n" +
                        "end\n"
            )
        }

        fun testPhiUpvalue() {
            runFragment(
                LuaValue.valueOf(6),
                "local a = foo or 0\n" +
                        "local function b(c)\n" +
                        "	if c > a then a = c end\n" +
                        "	return a\n" +
                        "end\n" +
                        "b(6)\n" +
                        "return a\n"
            )
        }

        fun testAssignReferUpvalues() {
            runFragment(
                LuaValue.valueOf(123),
                "local entity = 234\n" +
                        "local function c()\n" +
                        "    return entity\n" +
                        "end\n" +
                        "entity = (a == b) and 123\n" +
                        "if entity then\n" +
                        "    return entity\n" +
                        "end\n"
            )
        }

        fun testSimpleRepeatUntil() {
            runFragment(
                LuaValue.valueOf(5),
                "local a\n" +
                        "local w\n" +
                        "repeat\n" +
                        "	a = w\n" +
                        "until not a\n" +
                        "return 5\n"
            )
        }

        fun testLoopVarUpvalues() {
            runFragment(
                LuaValue.valueOf("b"),
                "local env = {}\n" +
                        "for a,b in pairs(_G) do\n" +
                        "	c = function()\n" +
                        "		return b\n" +
                        "	end\n" +
                        "end\n" +
                        "local e = env\n" +
                        "local f = {a='b'}\n" +
                        "for k,v in pairs(f) do\n" +
                        "	return env[k] or v\n" +
                        "end\n"
            )
        }

        fun testPhiVarUpvalue() {
            runFragment(
                LuaValue.valueOf(2),
                "local a = 1\n" +
                        "local function b()\n" +
                        "    a = a + 1\n" +
                        "    return function() end\n" +
                        "end\n" +
                        "for i in b() do\n" +
                        "	a = 3\n" +
                        "end\n" +
                        "return a\n"
            )
        }

        fun testUpvaluesInElseClauses() {
            runFragment(
                LuaValue.valueOf(111),
                "if a then\n" +
                        "   foo(bar)\n" +
                        "elseif _G then\n" +
                        "    local x = 111\n" +
                        "    if d then\n" +
                        "        foo(bar)\n" +
                        "    else\n" +
                        "    	local y = function()\n" +
                        "    		return x\n" +
                        "        end\n" +
                        "    	return y()\n" +
                        "    end\n" +
                        "end\n"
            )
        }

        fun testUpvalueInDoBlock() {
            runFragment(
                LuaValue.NONE!!, "do\n" +
                        "	local x = 10\n" +
                        "	function g()\n" +
                        "		return x\n" +
                        "	end\n" +
                        "end\n" +
                        "g()\n"
            )
        }

        fun testNullError() {
            // A nil error object becomes text at the point it is raised.
            runFragment(
                LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf("<no error object>"))!!,
                "return pcall(error)\n"
            )
        }

        fun testFindWithOffset() {
            runFragment(
                LuaValue.varargsOf(LuaValue.valueOf(8), LuaValue.valueOf(5))!!,
                "string = \"abcdef:ghi\"\n" +
                        "substring = string:sub(3)\n" +
                        "idx = substring:find(\":\")\n" +
                        "return #substring, idx\n"
            )
        }

        fun testErrorArgIsString() {
            runFragment(
                LuaValue.varargsOf(
                    net.blueva.luak.LuaValue.valueOf("string"),
                    net.blueva.luak.LuaValue.valueOf("c")
                )!!,
                "a,b = pcall(error, 'c'); return type(b), b\n"
            )
        }

        fun testErrorArgIsNil() {
            runFragment(
                LuaValue.varargsOf(
                    LuaValue.valueOf("string"),
                    LuaValue.valueOf("<no error object>"),
                )!!,
                "a,b = pcall(error); return type(b), b\n"
            )
        }

        fun testErrorArgIsTable() {
            runFragment(
                LuaValue.varargsOf(LuaValue.valueOf("table"), LuaValue.valueOf("d"))!!,
                "a,b = pcall(error, {c='d'}); return type(b), b.c\n"
            )
        }

        /** Only a string error object gets a position, so a number stays one. */
        fun testErrorArgIsNumber() {
            runFragment(
                LuaValue.varargsOf(
                    net.blueva.luak.LuaValue.valueOf("number"),
                    net.blueva.luak.LuaValue.valueOf(1L)
                )!!,
                "a,b = pcall(error, 1); return type(b), b\n"
            )
        }

        fun testErrorArgIsBool() {
            runFragment(
                LuaValue.varargsOf(LuaValue.valueOf("boolean"), LuaValue.TRUE!!)!!,
                "a,b = pcall(error, true); return type(b), b\n"
            )
        }

        fun testXpcallHandlerNotInvokedForInnerPcallError() {
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue>(
                        LuaValue.TRUE, LuaValue.FALSE, LuaValue.FALSE
                    )
                )!!,
                "local handlerRan = false\n" +
                        "local function handler(msg) handlerRan = true; return 'H:' .. tostring(msg) end\n" +
                        "local function inner()\n" +
                        "  local ok, err = pcall(function() return nil + 1 end)\n" +
                        "  return ok, err\n" +
                        "end\n" +
                        "local ok2, a = xpcall(inner, handler)\n" +
                        "return ok2, a, handlerRan\n"
            )
        }

        fun testBalancedMatchOnEmptyString() {
            runFragment(LuaValue.NIL, "return (\"\"):match(\"%b''\")\n")
        }

        fun testReturnValueForTableRemove() {
            // One value, which happens to be nil - not an absence of values.
            runFragment(LuaValue.NIL, "return table.remove({ })")
        }

        fun testTypeOfTableRemoveReturnValue() {
            runFragment(LuaValue.valueOf("nil"), "local k = table.remove({ }) return type(k)")
        }

        fun testVarargBugReport() {
            runFragment(
                LuaValue.varargsOf(
                    kotlin.arrayOf<net.blueva.luak.LuaValue?>(
                        net.blueva.luak.LuaValue.valueOf(1),
                        net.blueva.luak.LuaValue.valueOf(2),
                        net.blueva.luak.LuaValue.valueOf(3)
                    )
                )!!,
                ("local i = function(...) return ... end\n"
                        + "local v1, v2, v3 = i(1, 2, 3)\n"
                        + "return v1, v2, v3")
            )
        }
    }
}
