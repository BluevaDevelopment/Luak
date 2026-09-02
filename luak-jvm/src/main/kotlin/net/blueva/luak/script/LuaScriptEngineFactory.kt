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

import java.util.*
import javax.script.ScriptEngine
import javax.script.ScriptEngineFactory

/**
 * Jsr 223 scripting engine factory.
 * 
 * Exposes metadata to support the lua language, and constructs
 * instances of LuaScriptEngine to handl lua scripts.
 */
class LuaScriptEngineFactory : ScriptEngineFactory {
    private val extensions: MutableList<String?>?
    private val mimeTypes: MutableList<String?>?
    private val names: MutableList<String?>?

    init {
        extensions = Arrays.asList<String?>(*EXTENSIONS)
        mimeTypes = Arrays.asList<String?>(*MIMETYPES)
        names = Arrays.asList<String?>(*NAMES)
    }

    override fun getEngineName(): String? {
        return getScriptEngine().get(ScriptEngine.ENGINE).toString()
    }

    override fun getEngineVersion(): String? {
        return getScriptEngine().get(ScriptEngine.ENGINE_VERSION).toString()
    }

    override fun getExtensions(): MutableList<String?>? {
        return extensions
    }

    override fun getMimeTypes(): MutableList<String?>? {
        return mimeTypes
    }

    override fun getNames(): MutableList<String?>? {
        return names
    }

    override fun getLanguageName(): String? {
        return getScriptEngine().get(ScriptEngine.LANGUAGE).toString()
    }

    override fun getLanguageVersion(): String? {
        return getScriptEngine().get(ScriptEngine.LANGUAGE_VERSION).toString()
    }

    override fun getParameter(key: String?): Any? {
        return getScriptEngine().get(key).toString()
    }

    override fun getMethodCallSyntax(obj: String?, m: String?, vararg args: String?): String {
        val sb = StringBuffer()
        sb.append(obj + ":" + m + "(")
        val len = args.size
        for (i in 0..<len) {
            if (i > 0) {
                sb.append(',')
            }
            sb.append(args[i])
        }
        sb.append(")")
        return sb.toString()
    }

    override fun getOutputStatement(toDisplay: String): String {
        return "print(" + toDisplay + ")"
    }

    override fun getProgram(vararg statements: String?): String {
        val sb = StringBuffer()
        val len = statements.size
        for (i in 0..<len) {
            if (i > 0) {
                sb.append('\n')
            }
            sb.append(statements[i])
        }
        return sb.toString()
    }

    override fun getScriptEngine(): ScriptEngine {
        return LuaScriptEngine()
    }

    companion object {
        private val EXTENSIONS = arrayOf<String?>(
            "lua",
            ".lua",
        )

        private val MIMETYPES = arrayOf<String?>(
            "text/lua",
            "application/lua"
        )

        private val NAMES = arrayOf<String?>(
            "lua",
            "luak",
            "luaj",
        )
    }
}
