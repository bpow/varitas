package org.drpowell.vcffilters;

import java.io.Reader;
import java.util.List;
import java.util.logging.Logger;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFVariant;

public class ScriptVCFFilter extends VCFFilteringIterator {

	private Invocable invocable;
	private VCFHeaders headers;
	
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
		Object moreHeaders = engine.get("headers");
		if (moreHeaders != null) {
			if (moreHeaders instanceof String) {
				for (String header : ((String) moreHeaders).split("\n")) {
					headers.add(new VCFMeta(header));
				}
			} else if (moreHeaders instanceof List) {
				for (Object header : (List) moreHeaders) {
					headers.add(new VCFMeta(header.toString()));
				}
			} else {
				Logger.getLogger("VARITAS").warning("Unable to add headers for javascript filter");
			}
		}
		return (Invocable) engine;
	}

	public ScriptVCFFilter(VCFIterator client, Reader fileReader) {
		super(client);
		headers = client.getHeaders();
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
