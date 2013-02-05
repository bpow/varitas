package org.drpowell.varitas;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.drpowell.tabix.TabixReader;
import org.drpowell.util.GunzipIfGZipped;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFVariant;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

public class Varitas implements CLIRunnable {
	private ArrayList<Annotator> annotators = new ArrayList<Annotator>();
	private static Logger logger = Logger.getLogger("org.drpowell.varitas.GrandAnnotator");

	@Argument(alias = "c", description = "Configuration file (javascript)")
	private String config;
	
	@Argument(alias = "i", description = "Input file (VCF format, default is stdin)")
	private String infile;
	
	@Argument(alias = "o", description = "Output file (VCF format, default is stdout)")
	private String outfile;
	
	private File configFile;
	private File configParent;

	protected void initialize(String configFileName) throws IOException, ScriptException {
		configFile = new File(configFileName);
		configParent = configFile.getParentFile();
		annotators = readConfigFromJS(new BufferedReader(new FileReader(configFile)));
	}
	
	private ArrayList<Annotator> readConfigFromJS(Reader jsReader) throws ScriptException {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
		engine.eval("importPackage(" + this.getClass().getPackage().getName() + ");");
		engine.put("ga", this);
		engine.eval("addGeneAnnotator = function(id, file) { return ga.addGeneAnnotator(id, file); }");
		engine.eval("addSnpEffSplitter = function() { return ga.addSnpEffAnnotationSplitter(); }");
		engine.eval("addVCFAnnotator = function(file, fields) { return ga.addVCFAnnotator(file, fields); }");
		engine.eval("addTSVAnnotator = function(file, fields) { return ga.addTSVAnnotator(file, fields); }");
		engine.eval(jsReader);
		return annotators;
	}
	
	public GeneAnnotator addGeneAnnotator(String id, String fileName) {
		URL url = Main.findExistingFile(fileName, configParent);
		try {
			GeneAnnotator annotator = new GeneAnnotator(id, url);
			annotators.add(annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + ioe.toString());
		}
		return null;
	}
	
	public SnpEffAnnotationSplitter addSnpEffAnnotationSplitter() {
		// FIXME -- we really only need one of these
		SnpEffAnnotationSplitter a = new SnpEffAnnotationSplitter();
		annotators.add(0, a);
		return a;
	}

	public TabixVCFAnnotator addVCFAnnotator(String fileName, String fieldString) {
		URL url = Main.findExistingFile(fileName, configParent);
		try {
			TabixVCFAnnotator annotator = new TabixVCFAnnotator(new TabixReader(url.getFile()), fieldString);
			annotators.add(annotator);
			return annotator;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + e.toString());
		}
		return null;
	}

	public TabixTSVAnnotator addTSVAnnotator(String fileName, String fieldString) {
		URL url = Main.findExistingFile(fileName, configParent);
		try {
			TabixTSVAnnotator annotator = new TabixTSVAnnotator(new TabixReader(url.getFile()), fieldString);
			annotators.add(annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + ioe.toString());
		}
		return null;
	}
	
	public void annotateVCFFile(BufferedReader input) throws IOException {
		VCFParser parser = new VCFParser(input);
		for (VCFMeta meta: parser.getHeaders()) {
			System.out.println(meta);
		}
		// add additional info lines
		for (Annotator annotator: annotators) {
			for (String infoLine: annotator.infoLines()) {
				System.out.println(infoLine);
			}
		}
		System.out.println(parser.getHeaders().getColumnHeaderLine());

		for (VCFVariant variant: parser) {
			for (Annotator annotator: annotators) {
				annotator.annotate(variant);
			}
			System.out.println(variant);
		}
		System.out.flush();
	}

	public void doMain(List<String> extraArgs) {

		PrintStream ps;
		if (outfile != null) {
			try {
				ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(outfile), 1024));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				throw new RuntimeException("Unable to write to " + outfile);
			}
		} else {
			ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1024), false);
		}
		System.setOut(ps);
		
		try {
			BufferedReader input;
			if (infile != null) {
				input = GunzipIfGZipped.filenameToBufferedReader(infile);
			} else {
				input = new BufferedReader(new InputStreamReader(System.in));
			}
			initialize(config);
			annotateVCFFile(input);
			ps.close();
			input.close();
		} catch (Exception e) {
			e.printStackTrace();
			Args.usage(this);
		}
	}

}
