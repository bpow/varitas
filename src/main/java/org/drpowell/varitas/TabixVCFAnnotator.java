package org.drpowell.varitas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.drpowell.tabix.TabixReader;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFVariant;

public class TabixVCFAnnotator extends Annotator {
	private final TabixReader tabix;
	private final Map<String, String> fieldMap = new LinkedHashMap<String, String>();
	private boolean requirePass;
	private boolean copyID = false;

	public static final String stringJoin(String delimiter, String[] strings) {
		StringBuilder sb = new StringBuilder();
		for (String string : strings) {
			sb.append(string).append(delimiter);
		}
		return sb.substring(0, sb.length() - delimiter.length());
	}
	
	public TabixVCFAnnotator(final TabixReader reader, final Map<String, String> fields) {
		tabix = reader;
		fieldMap.putAll(fields);
	}
	
	public TabixVCFAnnotator(final TabixReader reader, String fieldString) {
		this.tabix = reader;
		String [] fields = fieldString.split(",");
		for (String field : fields) {
			int eq = field.indexOf("=");
			if (eq < 0) {
				fieldMap.put(field, field);
			} else {
				fieldMap.put(field.substring(0, eq), field.substring(eq+1));
			}
		}
	}
		
	@Override
	public VCFVariant annotate(VCFVariant variant) {
		String chromosome = variant.getSequence();
		Integer tid = tabix.getIdForChromosome(prefix + chromosome);
		if (tid == null) {
			// may want to log this...
			return variant;
		}
		String [] resultRow;
		int start = variant.getStart();
		int end = variant.getEnd();
		String ref = variant.getRef();
		String alt = variant.getAlt();
		// when using this query form, tabix expects space-based (0-based) coordinates
		Iterator<String []> iterator = tabix.getIndex().query(tid, start-1, end);
		while ((resultRow = iterator.next()) != null) {
			VCFVariant target = new VCFVariant(resultRow);
			// check on position (1), ref (3) and alt (4)
			if (target.getStart() == start &&
				target.getRef().equals(ref) &&
				target.getAlt().equals(alt)) {
				// FIXME - some target files will have more than one variant per line
				if (requirePass && !target.getFilter().equals("PASS")) {
					continue;
				}
				// found a match!
				for (Entry<String, String> e: fieldMap.entrySet()) {
					if (target.hasInfo(e.getKey())) {
						// FIXME- should check to prevent duplicates being overwritten
						variant.putInfo(e.getValue(), target.getInfoValue(e.getKey()));
					}
				}
				if (copyID) {
					variant.mergeID(target.getID());
				}
				break;
			}
		}		
		return variant;
	}
	
	public Annotator setRequirePass(boolean require) {
		requirePass = require;
		return this;
	}
	
	public Annotator setCopyID(boolean copyID) {
		this.copyID  = copyID;
		return this;
	}

	@Override
	public Iterable<String> infoLines() {
		ArrayList<String> infos = new ArrayList<String>();
		HashMap<String, String> newInfos = new HashMap<String, String>();
		try {
			for (String metaLine : tabix.readHeaders()) {
				String newId = null; 
				VCFMeta meta = new VCFMeta(metaLine);
				String oldId = meta.getValue("ID");
				if ("INFO".equals(meta.getMetaKey()) && (newId = fieldMap.get(oldId)) != null) {
					meta = meta.cloneExcept("ID", newId);
					newInfos.put(newId, meta.toString());
				}
			}
			// looping twice so we can iterate through the LinkedHashMap in its order
			for (String newId : fieldMap.values()) {
				String info = newInfos.get(newId);
				if (info != null) infos.add(info);
			}
		} catch (IOException e) {
			// FIXME should probably log this
			e.printStackTrace();
		}
		return infos;
	}

	@Override
	public String toString() {
		return "TabixVCFAnnotator: " + tabix.filename;
	}
}
