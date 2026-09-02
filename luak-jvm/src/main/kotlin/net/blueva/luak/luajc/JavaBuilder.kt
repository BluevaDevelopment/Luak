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
package net.blueva.luak.luajc

import net.blueva.luak.*
import net.blueva.luak.lib.*
import org.apache.bcel.Constants
import org.apache.bcel.generic.*
import java.io.ByteArrayOutputStream
import java.io.IOException

class JavaBuilder(// basic info
    private val pi: ProtoInfo, private val classname: String?, filename: String?
) {
    private val p: Prototype

    // bcel variables
    private val cg: ClassGen
    private val cp: ConstantPoolGen
    private val factory: InstructionFactory

    // main instruction list for the main function of this class
    private val init: InstructionList
    private val main: InstructionList
    private val mg: MethodGen

    // the superclass arg count, 0-3 args, 4=varargs
    private var superclassType: Int

    // storage for goto locations
    private val targets: IntArray
    private val branches: Array<BranchInstruction?>
    private val branchDestHandles: Array<InstructionHandle?>
    private val lastInstrHandles: Array<InstructionHandle?>
    private var beginningOfLuaInstruction: InstructionHandle? = null

    // hold vararg result
    private var varresult: LocalVariableGen? = null
    private var prev_line = -1

    fun initializeSlots() {
        var slot = 0
        createUpvalues(-1, 0, p.maxstacksize)
        if (superclassType == SUPERTYPE_VARARGS) {
            slot = 0
            while (slot < p.numparams) {
                if (pi.isInitialValueUsed(slot) == true) {
                    append(ALOAD(1))
                    append(PUSH(cp, slot + 1))
                    append(
                        factory.createInvoke(
                            STR_VARARGS,
                            "arg",
                            TYPE_LUAVALUE,
                            ARG_TYPES_INT,
                            Constants.INVOKEVIRTUAL
                        )
                    )
                    storeLocal(-1, slot)
                }
                slot++
            }
            append(ALOAD(1))
            append(PUSH(cp, 1 + p.numparams))
            append(factory.createInvoke(STR_VARARGS, "subargs", TYPE_VARARGS, ARG_TYPES_INT, Constants.INVOKEVIRTUAL))
            append(ASTORE(1))
        } else {
            // fixed arg function between 0 and 3 arguments
            slot = 0
            while (slot < p.numparams) {
                this.plainSlotVars.put(slot, 1 + slot)
                if (pi.isUpvalueCreate(-1, slot) == true) {
                    append(ALOAD(1 + slot))
                    storeLocal(-1, slot)
                }
                slot++
            }
        }


        // nil parameters 
        // TODO: remove this for lua 5.2, not needed
        while (slot < p.maxstacksize) {
            if (pi.isInitialValueUsed(slot) == true) {
                loadNil()
                storeLocal(-1, slot)
            }
            slot++
        }
    }

    fun completeClass(genmain: Boolean): ByteArray? {
        // add class initializer 

        if (!init.isEmpty()) {
            val mg = MethodGen(
                Constants.ACC_STATIC.toInt(), Type.VOID,
                ARG_TYPES_NONE, arrayOf<String?>(), "<clinit>",
                cg.getClassName(), init, cg.getConstantPool()
            )
            init.append(InstructionConstants.RETURN)
            mg.setMaxStack()
            cg.addMethod(mg.getMethod())
            init.dispose()
        }

        // add default constructor
        cg.addEmptyConstructor(Constants.ACC_PUBLIC.toInt())


        // gen method
        resolveBranches()
        mg.setMaxStack()
        cg.addMethod(mg.getMethod())
        main.dispose()

        // add initupvalue1(LuaValue env) to initialize environment for main chunk 
        if (p.upvalues!!.size == 1 && superclassType == SUPERTYPE_VARARGS) {
            val mg = MethodGen(
                Constants.ACC_PUBLIC.toInt() or Constants.ACC_FINAL.toInt(),  // access flags
                Type.VOID,  // return type
                ARG_TYPES_LUAVALUE,  // argument types
                arrayOf<String>("env"),  // arg names
                "initupvalue1",
                STR_LUAVALUE,  // method, defining class
                main, cp
            )
            val isrw = pi.isReadWriteUpvalue(pi.upvals!![0]!!) == true
            append(InstructionConstants.THIS)
            append(ALOAD(1))
            if (isrw) {
                append(
                    factory.createInvoke(
                        classname,
                        "newupl",
                        TYPE_LOCALUPVALUE,
                        ARG_TYPES_LUAVALUE,
                        Constants.INVOKESTATIC
                    )
                )
                append(factory.createFieldAccess(classname, upvalueName(0), TYPE_LOCALUPVALUE, Constants.PUTFIELD))
            } else {
                append(factory.createFieldAccess(classname, upvalueName(0), TYPE_LUAVALUE, Constants.PUTFIELD))
            }
            append(InstructionConstants.RETURN)
            mg.setMaxStack()
            cg.addMethod(mg.getMethod())
            main.dispose()
        }


        // add main function so class is invokable from the java command line 
        if (genmain) {
            val mg = MethodGen(
                Constants.ACC_PUBLIC.toInt() or Constants.ACC_STATIC.toInt(),  // access flags
                Type.VOID,  // return type
                ARG_TYPES_STRINGARRAY,  // argument types
                arrayOf<String>("arg"),  // arg names
                "main",
                classname,  // method, defining class
                main, cp
            )
            append(factory.createNew(classname))
            append(InstructionConstants.DUP)
            append(
                factory.createInvoke(
                    classname,
                    Constants.CONSTRUCTOR_NAME,
                    Type.VOID,
                    ARG_TYPES_NONE,
                    Constants.INVOKESPECIAL
                )
            )
            append(ALOAD(0))
            append(
                factory.createInvoke(
                    STR_JVMPLATFORM,
                    "luaMain",
                    Type.VOID,
                    ARG_TYPES_LUAVALUE_STRINGARRAY,
                    Constants.INVOKESTATIC
                )
            )
            append(InstructionConstants.RETURN)
            mg.setMaxStack()
            cg.addMethod(mg.getMethod())
            main.dispose()
        }


        // convert to class bytes
        try {
            val baos = ByteArrayOutputStream()
            cg.getJavaClass().dump(baos)
            return baos.toByteArray()
        } catch (ioe: IOException) {
            throw RuntimeException("JavaClass.dump() threw " + ioe)
        }
    }

    fun dup() {
        append(InstructionConstants.DUP)
    }

    fun pop() {
        append(InstructionConstants.POP)
    }

    fun loadNil() {
        append(factory.createFieldAccess(STR_LUAVALUE, "NIL", TYPE_LUAVALUE, Constants.GETSTATIC))
    }

    fun loadNone() {
        append(factory.createFieldAccess(STR_LUAVALUE, "NONE", TYPE_LUAVALUE, Constants.GETSTATIC))
    }

    fun loadBoolean(b: Boolean) {
        val field = (if (b) "TRUE" else "FALSE")
        append(factory.createFieldAccess(STR_LUAVALUE, field, TYPE_LUABOOLEAN, Constants.GETSTATIC))
    }

    private val plainSlotVars: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()
    private val upvalueSlotVars: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()
    private val localVarGenBySlot: MutableMap<Int?, LocalVariableGen?> = HashMap<Int?, LocalVariableGen?>()
    private fun findSlot(slot: Int, map: MutableMap<Int?, Int?>, prefix: String, type: Type?): Int {
        val islot = slot
        if (map.containsKey(islot)) return (map.get(islot) as Int)
        val name = prefix + slot
        val local = mg.addLocalVariable(name, type, null, null)
        val index = local.getIndex()
        map.put(islot, index)
        localVarGenBySlot.put(islot, local)
        return index
    }

    private fun findSlotIndex(slot: Int, isupvalue: Boolean): Int {
        return if (isupvalue) findSlot(
            slot,
            upvalueSlotVars,
            PREFIX_UPVALUE_SLOT,
            TYPE_LOCALUPVALUE
        ) else findSlot(slot, plainSlotVars, PREFIX_PLAIN_SLOT, TYPE_LUAVALUE)
    }

    fun loadLocal(pc: Int, slot: Int) {
        val isupval = pi.isUpvalueRefer(pc, slot) == true
        val index = findSlotIndex(slot, isupval)
        append(ALOAD(index))
        if (isupval) {
            append(PUSH(cp, 0))
            append(InstructionConstants.AALOAD)
        }
    }

    fun storeLocal(pc: Int, slot: Int) {
        val isupval = pi.isUpvalueAssign(pc, slot) == true
        val index = findSlotIndex(slot, isupval)
        if (isupval) {
            val isupcreate = pi.isUpvalueCreate(pc, slot) == true
            if (isupcreate) {
                append(
                    factory.createInvoke(
                        classname,
                        "newupe",
                        TYPE_LOCALUPVALUE,
                        ARG_TYPES_NONE,
                        Constants.INVOKESTATIC
                    )
                )
                append(InstructionConstants.DUP)
                append(ASTORE(index))
            } else {
                append(ALOAD(index))
            }
            append(InstructionConstants.SWAP)
            append(PUSH(cp, 0))
            append(InstructionConstants.SWAP)
            append(InstructionConstants.AASTORE)
        } else {
            append(ASTORE(index))
        }
    }

    fun createUpvalues(pc: Int, firstslot: Int, numslots: Int) {
        for (i in 0..<numslots) {
            val slot = firstslot + i
            val isupcreate = pi.isUpvalueCreate(pc, slot) == true
            if (isupcreate) {
                val index = findSlotIndex(slot, true)
                append(
                    factory.createInvoke(
                        classname,
                        "newupn",
                        TYPE_LOCALUPVALUE,
                        ARG_TYPES_NONE,
                        Constants.INVOKESTATIC
                    )
                )
                append(ASTORE(index))
            }
        }
    }

    fun convertToUpvalue(pc: Int, slot: Int) {
        val isupassign = pi.isUpvalueAssign(pc, slot) == true
        if (isupassign) {
            val index = findSlotIndex(slot, false)
            append(ALOAD(index))
            append(
                factory.createInvoke(
                    classname,
                    "newupl",
                    TYPE_LOCALUPVALUE,
                    ARG_TYPES_LUAVALUE,
                    Constants.INVOKESTATIC
                )
            )
            val upindex = findSlotIndex(slot, true)
            append(ASTORE(upindex))
        }
    }

    fun loadUpvalue(upindex: Int) {
        val isrw = pi.isReadWriteUpvalue(pi.upvals!![upindex]!!) == true
        append(InstructionConstants.THIS)
        if (isrw) {
            append(factory.createFieldAccess(classname, upvalueName(upindex), TYPE_LOCALUPVALUE, Constants.GETFIELD))
            append(PUSH(cp, 0))
            append(InstructionConstants.AALOAD)
        } else {
            append(factory.createFieldAccess(classname, upvalueName(upindex), TYPE_LUAVALUE, Constants.GETFIELD))
        }
    }

    fun storeUpvalue(pc: Int, upindex: Int, slot: Int) {
        val isrw = pi.isReadWriteUpvalue(pi.upvals!![upindex]!!) == true
        append(InstructionConstants.THIS)
        if (isrw) {
            append(factory.createFieldAccess(classname, upvalueName(upindex), TYPE_LOCALUPVALUE, Constants.GETFIELD))
            append(PUSH(cp, 0))
            loadLocal(pc, slot)
            append(InstructionConstants.AASTORE)
        } else {
            loadLocal(pc, slot)
            append(factory.createFieldAccess(classname, upvalueName(upindex), TYPE_LUAVALUE, Constants.PUTFIELD))
        }
    }


    fun newTable(b: Int, c: Int) {
        append(PUSH(cp, b))
        append(PUSH(cp, c))
        append(factory.createInvoke(STR_LUAVALUE, "tableOf", TYPE_LUATABLE, ARG_TYPES_INT_INT, Constants.INVOKESTATIC))
    }

    fun loadVarargs() {
        append(ALOAD(1))
    }

    fun loadVarargs(argindex: Int) {
        loadVarargs()
        arg(argindex)
    }

    fun arg(argindex: Int) {
        if (argindex == 1) {
            append(factory.createInvoke(STR_VARARGS, "arg1", TYPE_LUAVALUE, ARG_TYPES_NONE, Constants.INVOKEVIRTUAL))
        } else {
            append(PUSH(cp, argindex))
            append(factory.createInvoke(STR_VARARGS, "arg", TYPE_LUAVALUE, ARG_TYPES_INT, Constants.INVOKEVIRTUAL))
        }
    }

    private val varresultIndex: Int
        get() {
            if (varresult == null) varresult = mg.addLocalVariable(
                NAME_VARRESULT,
                TYPE_VARARGS,
                null,
                null
            )
            return varresult!!.getIndex()
        }

    fun loadVarresult() {
        append(ALOAD(this.varresultIndex))
    }

    fun storeVarresult() {
        append(ASTORE(this.varresultIndex))
    }

    fun subargs(firstarg: Int) {
        append(PUSH(cp, firstarg))
        append(factory.createInvoke(STR_VARARGS, "subargs", TYPE_VARARGS, ARG_TYPES_INT, Constants.INVOKEVIRTUAL))
    }

    fun getTable() {
        append(
            factory.createInvoke(
                STR_LUAVALUE,
                "get",
                TYPE_LUAVALUE,
                ARG_TYPES_LUAVALUE,
                Constants.INVOKEVIRTUAL
            )
        )
    }

    fun setTable() {
        append(
            factory.createInvoke(
                STR_LUAVALUE,
                "set",
                Type.VOID,
                ARG_TYPES_LUAVALUE_LUAVALUE,
                Constants.INVOKEVIRTUAL
            )
        )
    }

    fun unaryop(o: Int) {
        val op: String?
        when (o) {
            Lua.OP_UNM -> op = "neg"
            Lua.OP_NOT -> op = "not"
            Lua.OP_LEN -> op = "len"
            else -> op = "neg"
        }
        append(factory.createInvoke(STR_LUAVALUE, op, TYPE_LUAVALUE, Type.NO_ARGS, Constants.INVOKEVIRTUAL))
    }

    fun binaryop(o: Int) {
        val op: String?
        when (o) {
            Lua.OP_ADD -> op = "add"
            Lua.OP_SUB -> op = "sub"
            Lua.OP_MUL -> op = "mul"
            Lua.OP_DIV -> op = "div"
            Lua.OP_MOD -> op = "mod"
            Lua.OP_POW -> op = "pow"
            else -> op = "add"
        }
        append(factory.createInvoke(STR_LUAVALUE, op, TYPE_LUAVALUE, ARG_TYPES_LUAVALUE, Constants.INVOKEVIRTUAL))
    }

    fun compareop(o: Int) {
        val op: String?
        when (o) {
            Lua.OP_EQ -> op = "eq_b"
            Lua.OP_LT -> op = "lt_b"
            Lua.OP_LE -> op = "lteq_b"
            else -> op = "eq_b"
        }
        append(factory.createInvoke(STR_LUAVALUE, op, Type.BOOLEAN, ARG_TYPES_LUAVALUE, Constants.INVOKEVIRTUAL))
    }

    fun areturn() {
        append(InstructionConstants.ARETURN)
    }

    fun toBoolean() {
        append(factory.createInvoke(STR_LUAVALUE, "toboolean", Type.BOOLEAN, Type.NO_ARGS, Constants.INVOKEVIRTUAL))
    }

    fun tostring() {
        append(factory.createInvoke(STR_BUFFER, "tostring", TYPE_LUASTRING, Type.NO_ARGS, Constants.INVOKEVIRTUAL))
    }

    fun isNil() {
        append(
            factory.createInvoke(
                STR_LUAVALUE,
                "isnil",
                Type.BOOLEAN,
                Type.NO_ARGS,
                Constants.INVOKEVIRTUAL
            )
        )
    }

    fun testForLoop() {
        append(
            factory.createInvoke(
                STR_LUAVALUE,
                "testfor_b",
                Type.BOOLEAN,
                ARG_TYPES_LUAVALUE_LUAVALUE,
                Constants.INVOKEVIRTUAL
            )
        )
    }

    fun loadArrayArgs(pc: Int, firstslot: Int, nargs: Int) {
        var firstslot = firstslot
        append(PUSH(cp, nargs))
        append(ANEWARRAY(cp.addClass(STR_LUAVALUE)))
        for (i in 0..<nargs) {
            append(InstructionConstants.DUP)
            append(PUSH(cp, i))
            loadLocal(pc, firstslot++)
            append(AASTORE())
        }
    }

    fun newVarargs(pc: Int, firstslot: Int, nargs: Int) {
        when (nargs) {
            0 -> loadNone()
            1 -> loadLocal(pc, firstslot)
            2 -> {
                loadLocal(pc, firstslot)
                loadLocal(pc, firstslot + 1)
                append(
                    factory.createInvoke(
                        STR_LUAVALUE,
                        "varargsOf",
                        TYPE_VARARGS,
                        ARG_TYPES_LUAVALUE_VARARGS,
                        Constants.INVOKESTATIC
                    )
                )
            }

            3 -> {
                loadLocal(pc, firstslot)
                loadLocal(pc, firstslot + 1)
                loadLocal(pc, firstslot + 2)
                append(
                    factory.createInvoke(
                        STR_LUAVALUE,
                        "varargsOf",
                        TYPE_VARARGS,
                        ARG_TYPES_LUAVALUE_LUAVALUE_VARARGS,
                        Constants.INVOKESTATIC
                    )
                )
            }

            else -> {
                loadArrayArgs(pc, firstslot, nargs)
                append(
                    factory.createInvoke(
                        STR_LUAVALUE,
                        "varargsOf",
                        TYPE_VARARGS,
                        ARG_TYPES_LUAVALUEARRAY,
                        Constants.INVOKESTATIC
                    )
                )
            }
        }
    }

    fun newVarargsVarresult(pc: Int, firstslot: Int, nslots: Int) {
        loadArrayArgs(pc, firstslot, nslots)
        loadVarresult()
        append(
            factory.createInvoke(
                STR_LUAVALUE,
                "varargsOf",
                TYPE_VARARGS,
                ARG_TYPES_LUAVALUEARRAY_VARARGS,
                Constants.INVOKESTATIC
            )
        )
    }

    fun call(nargs: Int) {
        when (nargs) {
            0 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "call",
                    TYPE_LUAVALUE,
                    ARG_TYPES_NONE,
                    Constants.INVOKEVIRTUAL
                )
            )

            1 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "call",
                    TYPE_LUAVALUE,
                    ARG_TYPES_LUAVALUE,
                    Constants.INVOKEVIRTUAL
                )
            )

            2 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "call",
                    TYPE_LUAVALUE,
                    ARG_TYPES_LUAVALUE_LUAVALUE,
                    Constants.INVOKEVIRTUAL
                )
            )

            3 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "call",
                    TYPE_LUAVALUE,
                    ARG_TYPES_LUAVALUE_LUAVALUE_LUAVALUE,
                    Constants.INVOKEVIRTUAL
                )
            )

            else -> throw IllegalArgumentException("can't call with " + nargs + " args")
        }
    }

    fun newTailcallVarargs() {
        append(
            factory.createInvoke(
                STR_LUAVALUE,
                "tailcallOf",
                TYPE_VARARGS,
                ARG_TYPES_LUAVALUE_VARARGS,
                Constants.INVOKESTATIC
            )
        )
    }

    fun invoke(nargs: Int) {
        when (nargs) {
            -1 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "invoke",
                    TYPE_VARARGS,
                    ARG_TYPES_VARARGS,
                    Constants.INVOKEVIRTUAL
                )
            )

            0 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "invoke",
                    TYPE_VARARGS,
                    ARG_TYPES_NONE,
                    Constants.INVOKEVIRTUAL
                )
            )

            1 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "invoke",
                    TYPE_VARARGS,
                    ARG_TYPES_VARARGS,
                    Constants.INVOKEVIRTUAL
                )
            )

            2 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "invoke",
                    TYPE_VARARGS,
                    ARG_TYPES_LUAVALUE_VARARGS,
                    Constants.INVOKEVIRTUAL
                )
            )

            3 -> append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "invoke",
                    TYPE_VARARGS,
                    ARG_TYPES_LUAVALUE_LUAVALUE_VARARGS,
                    Constants.INVOKEVIRTUAL
                )
            )

            else -> throw IllegalArgumentException("can't invoke with " + nargs + " args")
        }
    }


    // ------------------------ closures ------------------------
    fun closureCreate(protoname: String) {
        append(factory.createNew(ObjectType(protoname)))
        append(InstructionConstants.DUP)
        append(factory.createInvoke(protoname, "<init>", Type.VOID, Type.NO_ARGS, Constants.INVOKESPECIAL))
    }

    fun closureInitUpvalueFromUpvalue(protoname: String?, newup: Int, upindex: Int) {
        val isrw = pi.isReadWriteUpvalue(pi.upvals!![upindex]!!) == true
        val uptype = if (isrw) TYPE_LOCALUPVALUE as Type else TYPE_LUAVALUE as Type
        val srcname: String = upvalueName(upindex)
        val destname: String = upvalueName(newup)
        append(InstructionConstants.THIS)
        append(factory.createFieldAccess(classname, srcname, uptype, Constants.GETFIELD))
        append(factory.createFieldAccess(protoname, destname, uptype, Constants.PUTFIELD))
    }

    fun closureInitUpvalueFromLocal(protoname: String?, newup: Int, pc: Int, srcslot: Int) {
        val isrw = pi.isReadWriteUpvalue(pi.vars[srcslot]!![pc]!!.upvalue!!) == true
        val uptype = if (isrw) TYPE_LOCALUPVALUE as Type else TYPE_LUAVALUE as Type
        val destname: String = upvalueName(newup)
        val index = findSlotIndex(srcslot, isrw)
        append(ALOAD(index))
        append(factory.createFieldAccess(protoname, destname, uptype, Constants.PUTFIELD))
    }

    private val constants: MutableMap<LuaValue?, String?> = HashMap<LuaValue?, String?>()

    fun loadConstant(value: LuaValue) {
        when (value.type()) {
            LuaValue.TNIL -> loadNil()
            LuaValue.TBOOLEAN -> loadBoolean(value.toboolean())
            LuaValue.TNUMBER, LuaValue.TSTRING -> {
                var name: String? = constants.get(value)
                if (name == null) {
                    name =
                        if (value.type() == LuaValue.TNUMBER) if (value.isinttype()) createLuaIntegerField(value.checkint()) else createLuaDoubleField(
                            value.checkdouble()
                        ) else createLuaStringField(value.checkstring()!!)
                    constants.put(value, name)
                }
                append(factory.createGetStatic(classname, name, TYPE_LUAVALUE))
            }

            else -> throw IllegalArgumentException("bad constant type: " + value.type())
        }
    }

    private fun createLuaIntegerField(value: Int): String {
        val name: String = PREFIX_CONSTANT + constants.size
        val fg = FieldGen(
            Constants.ACC_STATIC.toInt() or Constants.ACC_FINAL.toInt(),
            TYPE_LUAVALUE, name, cp
        )
        cg.addField(fg.getField())
        init.append(PUSH(cp, value))
        init.append(
            factory.createInvoke(
                STR_LUAVALUE, "valueOf",
                TYPE_LUAINTEGER, ARG_TYPES_INT, Constants.INVOKESTATIC
            )
        )
        init.append(factory.createPutStatic(classname, name, TYPE_LUAVALUE))
        return name
    }

    private fun createLuaDoubleField(value: Double): String {
        val name: String = PREFIX_CONSTANT + constants.size
        val fg = FieldGen(
            Constants.ACC_STATIC.toInt() or Constants.ACC_FINAL.toInt(),
            TYPE_LUAVALUE, name, cp
        )
        cg.addField(fg.getField())
        init.append(PUSH(cp, value))
        init.append(
            factory.createInvoke(
                STR_LUAVALUE, "valueOf",
                TYPE_LUANUMBER, ARG_TYPES_DOUBLE, Constants.INVOKESTATIC
            )
        )
        init.append(factory.createPutStatic(classname, name, TYPE_LUAVALUE))
        return name
    }

    private fun createLuaStringField(value: LuaString): String {
        val name: String = PREFIX_CONSTANT + constants.size
        val fg = FieldGen(
            Constants.ACC_STATIC.toInt() or Constants.ACC_FINAL.toInt(),
            TYPE_LUAVALUE, name, cp
        )
        cg.addField(fg.getField())
        val ls = value.checkstring()
        if (ls.isValidUtf8) {
            init.append(PUSH(cp, value.tojstring()))
            init.append(
                factory.createInvoke(
                    STR_LUASTRING, "valueOf",
                    TYPE_LUASTRING, ARG_TYPES_STRING, Constants.INVOKESTATIC
                )
            )
        } else {
            val c = CharArray(ls.m_length)
            for (j in 0..<ls.m_length) c[j] = (0xff and (ls.m_bytes[ls.m_offset + j]).toInt()).toChar()
            init.append(PUSH(cp, String(c)))
            init.append(
                factory.createInvoke(
                    STR_STRING, "toCharArray",
                    TYPE_CHARARRAY, Type.NO_ARGS,
                    Constants.INVOKEVIRTUAL
                )
            )
            init.append(
                factory.createInvoke(
                    STR_LUASTRING, "valueOf",
                    TYPE_LUASTRING, ARG_TYPES_CHARARRAY,
                    Constants.INVOKESTATIC
                )
            )
        }
        init.append(factory.createPutStatic(classname, name, TYPE_LUAVALUE))
        return name
    }

    init {
        this.p = pi.prototype


        // what class to inherit from
        superclassType = p.numparams
        if (p.is_vararg != 0 || superclassType >= SUPERTYPE_VARARGS) superclassType = SUPERTYPE_VARARGS
        run {
            var i = 0
            val n = p.code!!.size
            while (i < n) {
                val inst = p.code!![i]
                val o = Lua.GET_OPCODE(inst)
                if ((o == Lua.OP_TAILCALL) ||
                    ((o == Lua.OP_RETURN) && (Lua.GETARG_B(inst) < 1 || Lua.GETARG_B(inst) > 2))
                ) {
                    superclassType = SUPERTYPE_VARARGS
                    break
                }
                i++
            }
        }


        // create class generator
        cg = ClassGen(
            classname, SUPER_NAME_N[superclassType], filename,
            Constants.ACC_PUBLIC.toInt() or Constants.ACC_SUPER.toInt(), null
        )
        cp = cg.getConstantPool() // cg creates constant pool

        // main instruction lists
        factory = InstructionFactory(cg)
        init = InstructionList()
        main = InstructionList()

        // create the fields
        for (i in p.upvalues!!.indices) {
            val isrw = pi.isReadWriteUpvalue(pi.upvals!![i]!!) == true
            val uptype = if (isrw) TYPE_LOCALUPVALUE as Type else TYPE_LUAVALUE as Type
            val fg = FieldGen(0, uptype, upvalueName(i), cp)
            cg.addField(fg.getField())
        }


        // create the method
        mg = MethodGen(
            Constants.ACC_PUBLIC.toInt() or Constants.ACC_FINAL.toInt(),  // access flags
            RETURN_TYPE_N[superclassType],  // return type
            ARG_TYPES_N[superclassType],  // argument types
            ARG_NAMES_N[superclassType],  // arg names
            METH_NAME_N[superclassType],
            STR_LUAVALUE,  // method, defining class
            main, cp
        )


        // initialize the values in the slots
        initializeSlots()


        // initialize branching
        val nc = p.code!!.size
        targets = IntArray(nc)
        branches = arrayOfNulls<BranchInstruction>(nc)
        branchDestHandles = arrayOfNulls<InstructionHandle>(nc)
        lastInstrHandles = arrayOfNulls<InstructionHandle>(nc)
    }

    fun addBranch(pc: Int, branchType: Int, targetpc: Int) {
        when (branchType) {
            BRANCH_GOTO -> branches[pc] = GOTO(null)
            BRANCH_IFNE -> branches[pc] = IFNE(null)
            BRANCH_IFEQ -> branches[pc] = IFEQ(null)
            else -> branches[pc] = GOTO(null)
        }
        targets[pc] = targetpc
        append(branches[pc])
    }


    private fun append(i: Instruction?) {
        conditionalSetBeginningOfLua(main.append(i))
    }

    private fun append(i: CompoundInstruction?) {
        conditionalSetBeginningOfLua(main.append(i))
    }

    private fun append(i: BranchInstruction?) {
        conditionalSetBeginningOfLua(main.append(i))
    }

    private fun conditionalSetBeginningOfLua(ih: InstructionHandle?) {
        if (beginningOfLuaInstruction == null) beginningOfLuaInstruction = ih
    }

    fun onEndOfLuaInstruction(pc: Int, line: Int) {
        branchDestHandles[pc] = beginningOfLuaInstruction
        lastInstrHandles[pc] = main.getEnd()
        if (line != prev_line) mg.addLineNumber(beginningOfLuaInstruction, line.also { prev_line = it })
        beginningOfLuaInstruction = null
    }

    fun setVarStartEnd(slot: Int, start_pc: Int, end_pc: Int, name: String) {
        var name = name
        val islot = slot
        if (localVarGenBySlot.containsKey(islot)) {
            name = name.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val l = localVarGenBySlot.get(islot) as LocalVariableGen
            l.setEnd(lastInstrHandles[end_pc - 1])
            if (start_pc > 1) l.setStart(lastInstrHandles[start_pc - 2])
            l.setName(name)
        }
    }

    private fun resolveBranches() {
        val nc = p.code!!.size
        for (pc in 0..<nc) {
            if (branches[pc] != null) {
                var t = targets[pc]
                while (t < branchDestHandles.size && branchDestHandles[t] == null) t++
                require(t < branchDestHandles.size) { "no target at or after " + targets[pc] + " op=" + Lua.GET_OPCODE(p.code!![targets[pc]]) }
                branches[pc]!!.setTarget(branchDestHandles[t])
            }
        }
    }

    fun setlistStack(pc: Int, a0: Int, index0: Int, nvals: Int) {
        for (i in 0..<nvals) {
            dup()
            append(PUSH(cp, index0 + i))
            loadLocal(pc, a0 + i)
            append(
                factory.createInvoke(
                    STR_LUAVALUE,
                    "rawset",
                    Type.VOID,
                    ARG_TYPES_INT_LUAVALUE,
                    Constants.INVOKEVIRTUAL
                )
            )
        }
    }

    fun setlistVarargs(index0: Int, vresultbase: Int) {
        append(PUSH(cp, index0))
        loadVarresult()
        append(
            factory.createInvoke(
                STR_LUAVALUE,
                "rawsetlist",
                Type.VOID,
                ARG_TYPES_INT_VARARGS,
                Constants.INVOKEVIRTUAL
            )
        )
    }

    fun concatvalue() {
        append(factory.createInvoke(STR_LUAVALUE, "concat", TYPE_LUAVALUE, ARG_TYPES_LUAVALUE, Constants.INVOKEVIRTUAL))
    }

    fun concatbuffer() {
        append(factory.createInvoke(STR_LUAVALUE, "concat", TYPE_BUFFER, ARG_TYPES_BUFFER, Constants.INVOKEVIRTUAL))
    }

    fun tobuffer() {
        append(factory.createInvoke(STR_LUAVALUE, "buffer", TYPE_BUFFER, Type.NO_ARGS, Constants.INVOKEVIRTUAL))
    }

    fun tovalue() {
        append(factory.createInvoke(STR_BUFFER, "value", TYPE_LUAVALUE, Type.NO_ARGS, Constants.INVOKEVIRTUAL))
    }

    fun closeUpvalue(pc: Int, upindex: Int) {
        // TODO: assign the upvalue location the value null;
        /*
		boolean isrw = pi.isReadWriteUpvalue( pi.upvals[upindex] ); 
		append(InstructionConstants.THIS);
		append(InstructionConstants.ACONST_NULL);
		if ( isrw ) {
			append(factory.createFieldAccess(classname, upvalueName(upindex), TYPE_LUAVALUEARRAY, Constants.PUTFIELD));
		} else {
			append(factory.createFieldAccess(classname, upvalueName(upindex), TYPE_LUAVALUE, Constants.PUTFIELD));
		}
		*/
    }

    companion object {
        private val STR_VARARGS: String = Varargs::class.java.getName()
        private val STR_LUAVALUE: String = LuaValue::class.java.getName()
        private val STR_LUASTRING: String = LuaString::class.java.getName()
        private val STR_LUAINTEGER: String = LuaInteger::class.java.getName()
        private val STR_LUANUMBER: String = LuaNumber::class.java.getName()
        private val STR_LUABOOLEAN: String = LuaBoolean::class.java.getName()
        private val STR_LUATABLE: String = LuaTable::class.java.getName()
        private val STR_BUFFER: String = Buffer::class.java.getName()
        private val STR_STRING: String = String::class.java.getName()
        private const val STR_JVMPLATFORM = "net.blueva.luak.lib.jvm.JvmPlatform"

        private val TYPE_VARARGS = ObjectType(STR_VARARGS)
        private val TYPE_LUAVALUE = ObjectType(STR_LUAVALUE)
        private val TYPE_LUASTRING = ObjectType(STR_LUASTRING)
        private val TYPE_LUAINTEGER = ObjectType(STR_LUAINTEGER)
        private val TYPE_LUANUMBER = ObjectType(STR_LUANUMBER)
        private val TYPE_LUABOOLEAN = ObjectType(STR_LUABOOLEAN)
        private val TYPE_LUATABLE = ObjectType(STR_LUATABLE)
        private val TYPE_BUFFER = ObjectType(STR_BUFFER)
        private val TYPE_STRING = ObjectType(STR_STRING)

        private val TYPE_LOCALUPVALUE: ArrayType = ArrayType(TYPE_LUAVALUE, 1)
        private val TYPE_CHARARRAY = ArrayType(Type.CHAR, 1)
        private val TYPE_STRINGARRAY: ArrayType = ArrayType(TYPE_STRING, 1)


        private val STR_FUNCV: String = VarArgFunction::class.java.getName()
        private val STR_FUNC0: String = ZeroArgFunction::class.java.getName()
        private val STR_FUNC1: String = OneArgFunction::class.java.getName()
        private val STR_FUNC2: String = TwoArgFunction::class.java.getName()
        private val STR_FUNC3: String = ThreeArgFunction::class.java.getName()

        // argument list types
        private val ARG_TYPES_NONE = arrayOf<Type?>()
        private val ARG_TYPES_INT = arrayOf<Type?>(Type.INT)
        private val ARG_TYPES_DOUBLE = arrayOf<Type?>(Type.DOUBLE)
        private val ARG_TYPES_STRING = arrayOf<Type?>(Type.STRING)
        private val ARG_TYPES_CHARARRAY = arrayOf<Type?>(TYPE_CHARARRAY)
        private val ARG_TYPES_INT_LUAVALUE = arrayOf<Type?>(Type.INT, TYPE_LUAVALUE)
        private val ARG_TYPES_INT_VARARGS = arrayOf<Type?>(Type.INT, TYPE_VARARGS)
        private val ARG_TYPES_LUAVALUE_VARARGS = arrayOf<Type?>(TYPE_LUAVALUE, TYPE_VARARGS)
        private val ARG_TYPES_LUAVALUE_LUAVALUE_VARARGS = arrayOf<Type?>(TYPE_LUAVALUE, TYPE_LUAVALUE, TYPE_VARARGS)
        private val ARG_TYPES_LUAVALUEARRAY = arrayOf<Type?>(ArrayType(TYPE_LUAVALUE, 1))
        private val ARG_TYPES_LUAVALUEARRAY_VARARGS = arrayOf<Type?>(ArrayType(TYPE_LUAVALUE, 1), TYPE_VARARGS)
        private val ARG_TYPES_LUAVALUE_LUAVALUE_LUAVALUE = arrayOf<Type?>(TYPE_LUAVALUE, TYPE_LUAVALUE, TYPE_LUAVALUE)
        private val ARG_TYPES_VARARGS = arrayOf<Type?>(TYPE_VARARGS)
        private val ARG_TYPES_LUAVALUE_LUAVALUE = arrayOf<Type?>(TYPE_LUAVALUE, TYPE_LUAVALUE)
        private val ARG_TYPES_INT_INT = arrayOf<Type?>(Type.INT, Type.INT)
        private val ARG_TYPES_LUAVALUE = arrayOf<Type?>(TYPE_LUAVALUE)
        private val ARG_TYPES_BUFFER = arrayOf<Type?>(TYPE_BUFFER)
        private val ARG_TYPES_STRINGARRAY = arrayOf<Type?>(TYPE_STRINGARRAY)
        private val ARG_TYPES_LUAVALUE_STRINGARRAY = arrayOf<Type?>(TYPE_LUAVALUE, TYPE_STRINGARRAY)

        // names, arg types for main prototype classes
        private val SUPER_NAME_N = arrayOf<String?>(STR_FUNC0, STR_FUNC1, STR_FUNC2, STR_FUNC3, STR_FUNCV)
        private val RETURN_TYPE_N =
            arrayOf<ObjectType?>(TYPE_LUAVALUE, TYPE_LUAVALUE, TYPE_LUAVALUE, TYPE_LUAVALUE, TYPE_VARARGS)
        private val ARG_TYPES_N = arrayOf<Array<Type?>?>(
            ARG_TYPES_NONE,
            ARG_TYPES_LUAVALUE,
            ARG_TYPES_LUAVALUE_LUAVALUE,
            ARG_TYPES_LUAVALUE_LUAVALUE_LUAVALUE,
            ARG_TYPES_VARARGS,
        )
        private val ARG_NAMES_N = arrayOf<Array<String?>?>(
            arrayOf<String?>(),
            arrayOf<String?>("arg"),
            arrayOf<String?>("arg1", "arg2"),
            arrayOf<String?>("arg1", "arg2", "arg3"),
            arrayOf<String?>("args"),
        )
        private val METH_NAME_N = arrayOf<String?>("call", "call", "call", "call", "onInvoke")


        // varable naming
        private const val PREFIX_CONSTANT = "k"
        private const val PREFIX_UPVALUE = "u"
        private const val PREFIX_PLAIN_SLOT = "s"
        private const val PREFIX_UPVALUE_SLOT = "a"
        private const val NAME_VARRESULT = "v"

        private const val SUPERTYPE_VARARGS = 4

        private fun upvalueName(upindex: Int): String {
            return PREFIX_UPVALUE + upindex
        }

        // --------------------- branching support -------------------------
        const val BRANCH_GOTO: Int = 1
        const val BRANCH_IFNE: Int = 2
        const val BRANCH_IFEQ: Int = 3
    }
}
