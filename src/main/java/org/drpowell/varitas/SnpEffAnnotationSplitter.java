package org.drpowell.varitas;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * An annotator that splits out the info as provided by snpeff (http://snpeff.sourceforge.net).
 * 
 * The VCF field for snpeff output is described as follows for SNPEff versions before 3:
 * Effect ( Effect_Impact | Functional_Class | Codon_Change | Amino_Acid_change | Gene_Name | Gene_BioType | Coding | Transcript | Exon [ | ERRORS | WARNINGS ] )
 * 
 * For version 3.0 and later (so far):
 * Effect ( Effect_Impact | Functional_Class | Codon_Change | Amino_Acid_change| Amino_Acid_length | Gene_Name | Gene_BioType | Coding | Transcript | Exon [ | ERRORS | WARNINGS ] )
 * 
 * At some point later...
 * Effect ( Effect_Impact | Functional_Class | Codon_Change | Amino_Acid_Change| Amino_Acid_length | Gene_Name | Transcript_BioType | Gene_Coding | Transcript_ID | Exon_Rank  | Genotype_Number [ | ERRORS | WARNINGS ] )
 *
 * I'm going to default to the newest for now, but will need to figure out a way to make this configurable
 * @author Bradford Powell
 *
 */
public class SnpEffAnnotationSplitter extends Annotator {
	
	public static final String SNPEFF_INFO_TAG = "EFF";
	public static final String SNPEFF_FIELD_DELIMITER = "|";
	public static final VCFInfoHeaderLine[] EXTRA_HEADERS = {
			new VCFInfoHeaderLine("EFFECT", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "Effect type of the change (from SnpEff)")
			, new VCFInfoHeaderLine("Gene_name", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "Name of affected gene (from SnpEff)")
			, new VCFInfoHeaderLine("IMPACT", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "Impact of change (HIGH|MODERATE|LOW|MODIFIER)")
	};

	/**
	 * The effect types produced by SnpEff.
	 * 
	 * The order is as per http://snpeff.sourceforge.net/faq.html#How_is_impact_categorized?_(VCF_output)
	 *
	 */
	public enum Effect {
		// HIGH
		SPLICE_SITE_ACCEPTOR, SPLICE_SITE_DONOR, START_LOST, EXON_DELETED, FRAME_SHIFT,
		STOP_GAINED, STOP_LOST,	RARE_AMINO_ACID, CHROMOSOME_LARGE_DELETION,
		// MODERATE
		NON_SYNONYMOUS_CODING, CODON_CHANGE, CODON_INSERTION, CODON_CHANGE_PLUS_CODON_INSERTION,
		CODON_DELETION, CODON_CHANGE_PLUS_CODON_DELETION, SPLICE_SITE_BRANCH_U12, UTR_5_DELETED, UTR_3_DELETED,
		// LOW
		SYNONYMOUS_START, NON_SYNONYMOUS_START, START_GAINED, SYNONYMOUS_CODING,
		SYNONYMOUS_STOP, NON_SYNONYMOUS_STOP, SPLICE_SITE_BRANCH, SPLICE_SITE_REGION,
		// MODIFIER
		UTR_5_PRIME, UTR_3_PRIME, REGULATION, UPSTREAM, DOWNSTREAM, GENE, TRANSCRIPT, EXON,
		INTRON_CONSERVED, INTRON, INTRAGENIC, INTERGENIC, INTERGENIC_CONSERVED,
		NONE, CHROMOSOME, CUSTOM, CDS
	};

	public enum SnpEffAnnotationField {
		IMPACT, FUNCTIONAL_CLASS, CODON_CHANGE, AMINO_ACID_CHANGE, AMINO_ACID_LENGTH,
		GENE_NAME, GENE_BIOTYPE, TRANSCRIPT, EXON, ERRORS, WARNINGS;
		public static final int size = values().length;
	};

	public enum SnpEffImpact { HIGH, MODERATE, LOW, MODIFIER };
	
	private class SNPEffectVCFInfo implements Comparable<SNPEffectVCFInfo> {
		public final Effect effect;
		public final String [] annotations;
		
		public String toString() {
			StringBuilder sb = new StringBuilder(effect.toString()).append("(");
			for (String annotation : annotations) {
				sb.append(annotation).append(SNPEFF_FIELD_DELIMITER);
			}
			sb.setCharAt(sb.length()-2, ')');
			return sb.toString();
		}
		
		public SNPEffectVCFInfo(String encoded) {
			int openParan = encoded.indexOf('(');
			effect = Effect.valueOf(encoded.substring(0, openParan));
			String [] annos = encoded.substring(openParan+1, encoded.length()-1).split(Pattern.quote(SNPEFF_FIELD_DELIMITER), SnpEffAnnotationField.size);
			if (annos.length == SnpEffAnnotationField.size) {
				annotations = annos;
			} else {
				annotations = Arrays.copyOf(annos, SnpEffAnnotationField.size);
				for (int i = annos.length; i < annotations.length; i++) {
					annotations[i] = "";
				}
			}
		}
		
		public String get(SnpEffAnnotationField field) {
			return annotations[field.ordinal()];
		}
		
		@SuppressWarnings("unused")
		public SnpEffImpact getImpact() {
			return SnpEffImpact.valueOf(annotations[SnpEffAnnotationField.IMPACT.ordinal()]);
		}
		
		@Override
		public int compareTo(SNPEffectVCFInfo o) {
			int cmp = effect.compareTo(o.effect);
			// ideally this would compare by gene name before aa change, but for simplicity just work through the fields
			int i = 0;
			while (cmp == 0 && i < annotations.length) {
				cmp = annotations[i].compareTo(o.annotations[i]);
				i++;
			}
			return cmp;
		}
	}

	@Override
	public VariantContext annotate(VariantContext variant) {
		String effects = variant.getAttributeAsString(SNPEFF_INFO_TAG, null);
		if (effects != null) {
			VariantContextBuilder builder = new VariantContextBuilder(variant);
			ArrayList<SNPEffectVCFInfo> effList = new ArrayList<SNPEffectVCFInfo>();
			for (String s: effects.split(",")) {
				effList.add(new SNPEffectVCFInfo(s));
			}
			Collections.sort(effList);
			builder.attribute("EFFECT", effList.get(0).effect.toString());
			builder.attribute("Gene_name", effList.get(0).get(SnpEffAnnotationField.GENE_NAME));
			builder.attribute("IMPACT", effList.get(0).get(SnpEffAnnotationField.IMPACT));
			// FIXME = include others in addition to the first...
		}
		return variant;
	}

	@Override
	public Iterable<VCFInfoHeaderLine> infoLines() {
		return Arrays.asList(EXTRA_HEADERS);
	}
	
	@Override
	public String toString() {
		return "SnpEffSplitter";
	}

	public SnpEffAnnotationSplitter() {
		// nothing to do here
	}
}
