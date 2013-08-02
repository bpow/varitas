package org.drpowell.vcffilters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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

public class ScriptGroovyVCFFilter extends VCFFilteringIterator {

	private Invocable invocable;
	private VCFHeaders headers;
	
	private Invocable initializeEngine(File scriptFile) {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("groovy");
		try {
			engine.eval("import org.drpowell.varitas.*\nimport org.drpowell.vcf.*\n");
			engine.eval(new FileReader(scriptFile));
		} catch (ScriptException e) {
			System.err.println("Error preparing filter, will pass all variants!\n" + e);
			return null;
		} catch (FileNotFoundException e) {
			System.err.println("Error reading filter file, will pass all variants!\n" + e);
			return null;			
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
				Logger.getLogger("VARITAS").warning("Unable to add headers for groovy filter");
			}
		}
		return (Invocable) engine;
	}

	public ScriptGroovyVCFFilter(VCFIterator client, File scriptFile) {
		super(client);
		headers = client.getHeaders();
		invocable = initializeEngine(scriptFile);
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
