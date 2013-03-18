package org.drpowell.xlifyvcf;

import java.util.logging.Logger;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.drpowell.varitas.VCFFilteringIterator;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFVariant;

public class JavascriptBooleanVCFFilter extends VCFFilteringIterator {

	private final CompiledScript script;
	private final String filter;
	
	private CompiledScript initializeEngine(String filter) {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("JavaScript");
		CompiledScript cs;
		try {
			cs = ((Compilable) engine).compile("with (variant) { " + filter + "}");
		} catch (ScriptException e) {
			Logger.getLogger(this.getClass().getName()).severe("Error preparing this filter, it will pass all variants:\n" + filter);
			cs = null;
		}
		return cs;
	}

	public JavascriptBooleanVCFFilter(VCFIterator client, String filter) {
		super(client);
		this.filter = filter;
		script = initializeEngine(filter);
	}

	@Override
	public VCFVariant filter(VCFVariant variant) {
		if (script == null) return variant;
		script.getEngine().getBindings(ScriptContext.ENGINE_SCOPE).put("variant", variant);
		Object o;
		try {
			o = script.eval();
			if (o == null || o == Boolean.FALSE) {
				return null;
			}
		} catch (ScriptException e) {
			Logger.getLogger(this.getClass().getName()).warning("error processing filter: " + filter +
					"\n  For variant\n    " + variant + "\n  Will pass the variant to be on the safe side");
		}
		return variant;
	}

}
