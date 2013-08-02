package org.drpowell.vcffilters;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
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
	
	private Invocable initializeEngine(URL filterURL, String language) {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName(language);
		try {
			// FIXME-- why doesn't this work? Can I use a CompilerConfiguration with JSR223?
//			if ("groovy".equalsIgnoreCase(language)) {
//				engine.eval("import org.drpowell.vcf.*\nimport org.drpowell.varitas.*\nimport org.drpowell.vcffilters.*\n\n");
//			}
			engine.eval(new InputStreamReader(filterURL.openStream()));
		} catch (ScriptException e) {
			Logger.getLogger("VARITAS").severe("Error preparing filter from " +
					filterURL.toString() + ", will pass all variants!\n" + e);
			engine = null;
		} catch (IOException e) {
			Logger.getLogger("VARITAS").severe("Error reading filter from " +
					filterURL.toString() + ", will pass all variants!\n" + e);
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
				Logger.getLogger("VARITAS").warning("Unable to add headers for script filter");
			}
		}
		return (Invocable) engine;
	}

	public ScriptVCFFilter(VCFIterator client, URL filterURL, String language) {
		super(client);
		headers = client.getHeaders();
		invocable = initializeEngine(filterURL, language);
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
