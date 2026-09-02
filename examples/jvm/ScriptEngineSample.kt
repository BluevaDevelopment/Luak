import net.blueva.luak.LuaValue
import net.blueva.luak.lib.OneArgFunction
import javax.script.Compilable
import javax.script.ScriptEngineManager

/** Uses Luak through the JSR-223 scripting API. */
fun main() {
    val engine = ScriptEngineManager().getEngineByName("luaj")
    println(engine.factory.getOutputStatement("\"hello, world\""))
    engine.eval("print('hello, world')")
    engine.put("square", object : OneArgFunction() {
        override fun call(arg: LuaValue?): LuaValue = LuaValue.valueOf(arg!!.todouble() * arg.todouble())
    })
    println(engine.eval("return square(12)"))

    val compiled = (engine as Compilable).compile("return math.sqrt(x)")
    val bindings = engine.createBindings().apply { put("x", 144) }
    println(compiled.eval(bindings))
}
