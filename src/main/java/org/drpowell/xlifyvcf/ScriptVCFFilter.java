package org.drpowell.xlifyvcf;

import java.io.Reader;
import java.util.Iterator;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.drpowell.util.FilteringIterator;
import org.drpowell.vcf.VCFVariant;

public class ScriptVCFFilter extends FilteringIterator<VCFVariant> {

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

	public ScriptVCFFilter(Iterator<VCFVariant> client, Reader fileReader) {
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
