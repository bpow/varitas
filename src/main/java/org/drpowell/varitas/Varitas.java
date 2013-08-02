package org.drpowell.varitas;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.drpowell.acclimate.CLIParser;
import org.drpowell.acclimate.Option;
import org.drpowell.tabix.TabixReader;
import org.drpowell.util.FileUtils;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;
import org.drpowell.vcffilters.CompoundMutationFilter;
import org.drpowell.vcffilters.JavascriptBooleanVCFFilter;
import org.drpowell.vcffilters.MendelianConstraintFilter;
import org.drpowell.vcffilters.ScriptGroovyVCFFilter;
import org.drpowell.vcffilters.ScriptVCFFilter;
import org.drpowell.vcffilters.TSVWritingFilter;
import org.drpowell.vcffilters.VCFWritingFilter;
import org.drpowell.vcffilters.VariantOutput;
import org.drpowell.vcffilters.XLifyVcf;


public class Varitas implements Iterable<VCFVariant> {
	private VCFIterator variants = null;
	private static Logger logger = Logger.getLogger("Varitas");
	private File configParent;

	@Option(name = "-c", aliases = {"--config"}, usage = "configuration file (.js) for variant annotation")
	public VCFIterator applyConfig(String filename) {
		File configFile = new File(filename);
		configParent = configFile.getParentFile();
		try {
			return applyConfig(new FileInputStream(configFile));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return variants;
	}
	
	@Option(name = "-C", aliases = {"--defaultConfig"}, usage = "use default annotation configuration")
	public VCFIterator applyDefaultConfig() {
		configParent = new File(FileUtils.getJarContainingClass(this.getClass()).getPath()).getParentFile();
		InputStream configStream = getClass().getResourceAsStream("/config.js");
		return applyConfig(configStream);
	}
	
	private VCFIterator applyConfig(InputStream configStream) {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
		Scanner s;
		try {
			s = new Scanner(configStream);
			String jsString = s.useDelimiter("\\A").next();
			s.close();
			engine.put("__varitas", this);
			engine.eval("with (__varitas) {" + jsString + "}");
		} catch (ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this.variants;		
	}
	
	@Option(name = "-f", aliases = {"--filter"}, usage = "script file(s) by which to filter variants")
	public VCFIterator applyFilter(String filename) {
		try {
			return applyFilter(FileUtils.findExistingFile(filename).openStream());
		} catch (IOException e) {
			logger.severe("Error trying to read filter file [ " + filename + " ], will ignore this filter!");
			logger.severe(e.getMessage());
		}
		return variants;
	}
	
	@Option(name = "-F", aliases = {"--defaultFilter"}, usage = "apply default variant filter")
	public ScriptVCFFilter applyDefaultFilter() {
		return applyFilter(getClass().getResourceAsStream("/defaultVariantFilter.js"));
	}
	
	private ScriptVCFFilter applyFilter(InputStream filterStream) {
		ScriptVCFFilter filter = new ScriptVCFFilter(variants, new InputStreamReader(filterStream));
		variants = filter;
		return filter;
	}
	
	@Option(name = "-g", aliases = {"--groovyFilter"}, usage = "groovy script file by which to filter variants")
	public VCFIterator applyGroovyFilter(String filename) {
		variants = new ScriptGroovyVCFFilter(variants, new File(FileUtils.findExistingFile(filename).getFile()));
//		variants = new GroovyClassVCFFilter(variants, FileUtils.findExistingFile(filename));
		return variants;
	}
	
	@Option(name = "-j", aliases = {"--jsBoolean"}, usage = "javascript by which to filter variants (if result is true, the variant passes)")
	public JavascriptBooleanVCFFilter jsBoolean(String filter) {
		JavascriptBooleanVCFFilter f = new JavascriptBooleanVCFFilter(variants, filter);
		variants = f;
		return f;
	}
	
	@Option(name = "-i", aliases = {"--input"}, usage = "input file of variants (VCF format, provide '-' to read from stdin)", required = true, defaultArguments = {"-"}, priority = -1)
	public Varitas setInput(String input) {
		try {
			if (variants != null) {
				String message = "Attempted to set input file more than once (or after filter(s) applied): \n" + input;
				logger.severe(message);
				throw new RuntimeException(message);
			}
			if ("-".equals(input)) {
				variants = new VCFParser(new BufferedReader(new InputStreamReader(System.in)));
			} else {
				variants = new VCFParser(FileUtils.filenameToBufferedReader(input));
			}
		} catch (IOException e) {
			logger.severe("Error reading input file (" + input + "): " + e.getMessage());
			throw new RuntimeException("IO Error reading input file", e);
		}
		return this;
	}
	
	@Option(name = "-o", aliases = {"--output"}, usage = "output (.vcf) file (if none of -o, -x or -t are provided, will write to stdout)")
	public VCFIterator addOutput(String output) {
		PrintWriter writer;
		if (output == null || "-".equals(output)) {
			writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1024));
		} else {
			try {
				writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(output), 1024));
			} catch (FileNotFoundException e) {
				String message = "Unable to write to output file: " + output;
				logger.severe(message);
				throw new RuntimeException(message, e);
			}
		}
		variants = new VCFWritingFilter(variants, writer);
		return variants;
	}
	
	@Option(name = "-x", aliases = {"--xlsOutput"}, usage = "output (.xls) file")
	public VCFIterator addXlsOutput(String output) {
		OutputStream os;
		try {
			os = new BufferedOutputStream(new FileOutputStream(output), 1024);
			variants = new XLifyVcf(variants, os);
		} catch (FileNotFoundException e) {
			String message = "Unable to write to output file: " + output;
			logger.severe(message);
			e.printStackTrace();
			// TODO - decide whether to halt execution
		}
		return variants;
	}
	
	@Option(name = "-t", aliases = {"--tsvOutput"}, usage = "output (.tsv) file")
	public VCFIterator addTsvOutput(String output) {
		OutputStream os;
		try {
			if ("-".equals(output)) {
				os = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1024);
			} else {
				os = new BufferedOutputStream(new FileOutputStream(output), 1024);
			}
			variants = new TSVWritingFilter(variants, os);
		} catch (FileNotFoundException e) {
			String message = "Unable to write to output file: " + output;
			logger.severe(message);
			e.printStackTrace();
			// TODO - decide whether to halt execution
		}
		return variants;
	}
	
	@Option(name = "-b", aliases = {"--biallelic"}, usage = "apply biallelic filter")
	public VCFIterator applyBiallelicFilter() {
		List<int []> trios = VCFUtils.getTrioIndices(variants.getHeaders());
		// FIXME-- only handles a single trio
		if (!trios.isEmpty()) {
			variants = new CompoundMutationFilter(variants, trios.get(0));
		}
		return variants;
	}
	
	@Option(name = "-m", aliases = {"--mendelianContstraint"}, usage = "apply mendelian constraint filter")
	public VCFIterator applyMendelianConstraintFilter() {
		variants = new MendelianConstraintFilter(variants);
		return variants;
	}
	
	@Option(name = "-a", aliases = {"--addHeaders"}, usage = "file with additional headers to add to input vcf file")
	public void addHeadersFromFile(String filename) {
		VCFHeaders vcfHeaders = variants.getHeaders(); // FIXME-- this will only work if -a is first option!
		URL url = FileUtils.findExistingFile(filename);
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			String line = null;
			while ((line = br.readLine()) != null) {
				vcfHeaders.add(new VCFMeta(line));
			}
			br.close();
		} catch (IOException ioe) {
			Logger.getLogger("VARITAS").log(Level.SEVERE, "Problem reading additional headers from " + url, ioe);
		}
	}
	
	public GeneAnnotator geneAnnotator(String id, String fileName) {
		URL url = FileUtils.findExistingFile(fileName, configParent);
		try {
			GeneAnnotator annotator = new GeneAnnotator(id, url);
			variants = new AnnotatingIterator(variants, annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + ioe.toString());
		}
		return null;
	}
	
	public SnpEffAnnotationSplitter snpEffSplitter() {
		// FIXME -- we really only need one of these, and it should go before any gene annotators
		SnpEffAnnotationSplitter a = new SnpEffAnnotationSplitter();
		variants = new AnnotatingIterator(variants, a);
		return a;
	}

	public TabixVCFAnnotator vcfAnnotator(String fileName, String fieldString) {
		URL url = FileUtils.findExistingFile(fileName, configParent);
		if (url == null) {
			logger.severe("Unable to read file '" + fileName + "'");
			return null;
		}
		try {
			TabixVCFAnnotator annotator = new TabixVCFAnnotator(new TabixReader(url.getFile()), fieldString);
			variants = new AnnotatingIterator(variants, annotator);
			return annotator;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + e.toString());
		}
		return null;
	}

	public TabixTSVAnnotator tsvAnnotator(String fileName, String fieldString) {
		URL url = FileUtils.findExistingFile(fileName, configParent);
		if (url == null) {
			logger.severe("Unable to read file '" + fileName + "'");
			return null;
		}
		try {
			TabixTSVAnnotator annotator = new TabixTSVAnnotator(new TabixReader(url.getFile()), fieldString);
			variants = new AnnotatingIterator(variants, annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + ioe.toString());
		}
		return null;
	}
	
	@Override
	public Iterator<VCFVariant> iterator() {
		return variants;
	}
	
	public static void main(String... args) throws InvocationTargetException, IllegalAccessException {
		Varitas varitas = new Varitas();
		CLIParser<Varitas> cli = new CLIParser<Varitas>(varitas).interpret(args);
		if (args.length == 0) {
			System.err.println(cli.usage(false));
			System.exit(-1);
		}
		cli.apply();
		Iterator<VCFVariant> variants = varitas.iterator();
		if (!(variants instanceof VariantOutput)) {
			logger.info("The last element of the filter chain does not produce any output, will write to stdout");
			variants = varitas.addOutput(null);
		}
		
		while (variants.hasNext()) {
			variants.next();
		}
	}
	
}
