package org.drpowell.xlifyvcf;

import java.io.Reader;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.drpowell.varitas.VCFFilteringIterator;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFVariant;

public class ScriptVCFFilter extends VCFFilteringIterator {

	private Invocable invocable;
	
	private Invocable initializeEngine(Reader script) {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("JavaScript");
		try {
			engine.eval(script);
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			System.err.println("Error preparing filter, will pass all variants!\n" + e);
			engine = null;
		}
		return (Invocable) engine;
	}

	public ScriptVCFFilter(VCFIterator client, Reader fileReader) {
		super(client);
		invocable = initializeEngine(fileReader);
	}

	@Override
	public VCFVariant filter(VCFVariant variant) {
		if (invocable == null) return variant;
		try {
			return (VCFVariant) invocable.invokeFunction("filter", variant);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
			return variant;
		} catch (ScriptException e) {
			e.printStackTrace();
			return variant;
		}
	}

}
