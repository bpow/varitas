package org.drpowell.varitas;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;

import org.drpowell.xlifyvcf.XLifyVcf;

import com.sampullara.cli.Args;

public class Main {
	
	public static final Class<?>[] commands = {
		Varitas.class,
		XLifyVcf.class
	};
	
	public static URL findExistingFile(String f) {
		try {
			if (new File(f).exists()) return new File(f).toURI().toURL();
			Logger logger = Logger.getLogger("VARITAS");
			File attempt = new File(System.getProperty("user.dir"), f);
			logger.config("Trying " + attempt.getPath());
			if (attempt.exists()) return attempt.toURI().toURL();

			URL resource = ClassLoader.getSystemResource(f);
			if (resource != null) return resource;

			attempt = new File(System.getProperty("user.home"), f);
			logger.config("Trying " + attempt.getPath());
			if (attempt.exists()) return attempt.toURI().toURL();
		} catch (MalformedURLException e) {
			System.err.println(e);
		}
		
		// give up
		return null;
	}

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		if (args.length > 0) {
			for (Class<?> c : commands) {
				if (args[0].equals(c.getSimpleName())) {
					CLIRunnable command = (CLIRunnable) c.newInstance();
					String [] commandArgs = new String[args.length - 1];
					System.arraycopy(args, 1, commandArgs, 0, commandArgs.length);
					List<String> extras = Args.parse(command, commandArgs);
					command.doMain(extras);
					System.exit(0);
				}
			}
		}
		System.err.println("A command/program must be provided. Available programs are:");
		for (Class<?> c : commands) {
			System.err.println("\t" + c.getSimpleName());
		}
	}

}
