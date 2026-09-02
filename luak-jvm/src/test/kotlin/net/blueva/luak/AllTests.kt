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

import junit.framework.Test
import junit.framework.TestSuite
import net.blueva.luak.WeakTableTest.*
import net.blueva.luak.compiler.*
import net.blueva.luak.lib.jvm.JvmPlatformTest
import net.blueva.luak.lib.jvm.LuaJavaCoercionTest
import net.blueva.luak.lib.jvm.LuajavaAccessibleMembersTest
import net.blueva.luak.lib.jvm.LuajavaClassMembersTest
import net.blueva.luak.script.ScriptEngineTests

object AllTests {
    fun suite(): Test {
        val suite = TestSuite("All Tests for Luaj-vm2")

        // vm tests
        val vm = TestSuite("VM Tests")
        vm.addTestSuite(TypeTest::class.java)
        vm.addTestSuite(UnaryBinaryOperatorsTest::class.java)
        vm.addTestSuite(MetatableTest::class.java)
        vm.addTestSuite(LuaOperationsTest::class.java)
        vm.addTestSuite(StringTest::class.java)
        vm.addTestSuite(OrphanedThreadTest::class.java)
        vm.addTestSuite(VarargsTest::class.java)
        vm.addTestSuite(LoadOrderTest::class.java)
        suite.addTest(vm)

        // table tests
        val table = TestSuite("Table Tests")
        table.addTestSuite(TableTest::class.java)
        table.addTestSuite(TableHashTest::class.java)
        table.addTestSuite(WeakValueTableTest::class.java)
        table.addTestSuite(WeakKeyTableTest::class.java)
        table.addTestSuite(WeakKeyValueTableTest::class.java)
        suite.addTest(table)


        // bytecode compilers regression tests
        val bytecodetests = FragmentsTest.suite()
        suite.addTest(bytecodetests)


        // I/O tests
        val io = TestSuite("I/O Tests")
        io.addTestSuite(BufferedStreamTest::class.java)
        io.addTestSuite(UTF8StreamTest::class.java)
        suite.addTest(io)


        // prototype compiler
        val compiler = TestSuite("Lua Compiler Tests")
        compiler.addTestSuite(CompilerUnitTests::class.java)
        compiler.addTestSuite(DumpLoadEndianIntTest::class.java)
        compiler.addTestSuite(RegressionTests::class.java)
        compiler.addTestSuite(SimpleTests::class.java)
        suite.addTest(compiler)


        // library tests
        val lib = TestSuite("Library Tests")
        lib.addTestSuite(JvmPlatformTest::class.java)
        lib.addTestSuite(LuajavaAccessibleMembersTest::class.java)
        lib.addTestSuite(LuajavaClassMembersTest::class.java)
        lib.addTestSuite(LuaJavaCoercionTest::class.java)
        lib.addTestSuite(RequireClassTest::class.java)
        suite.addTest(lib)

        // Script engine tests.
        val script = ScriptEngineTests.suite()
        suite.addTest(script)


        // compatiblity tests
        val compat = CompatibiltyTest.suite()
        suite.addTest(compat)
        compat.addTestSuite(ErrorsTest::class.java)

        return suite
    }
}
