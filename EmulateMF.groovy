import java.util.regex.Pattern
import org.drpowell.vcf.*

headers = ['#FILTER=<ID=UnlikelyDeleterious,Description="Unlikely to be deleterious">',
           '#FILTER=<ID=Common,Description="MAF >= 1% in some population (5% for clinical variants)">']

def filter(VCFVariant v) {
	boolean common = false;
	float cutoff = 0.01
	if (isClinical(v)) {
		cutoff = 0.05
	}
	float aavar = numeric(v.getInfoValue("AV"))
	float aaaf = 0
	if (aavar > 0) {
		float aatot = numeric(v.getInfoValue("AT")) + aavar
		if (aavar / aatot > cutoff) {
			common = true;
		}
	}
	float eavar = numeric(v.getInfoValue("EV"))
	if (eavar > 0) {
		float eatot = numeric(v.getInfoValue("ET")) + eavar
		if (eavar / eatot > cutoff) {
			common = true;
		}
	}
	if (numeric(v.getInfoValue("TMAF")) > cutoff) {
		common = true;
	}
	if (common) {
		v.addFilter("Common");
	}
	if (!isSNPDeleterious(v)) {
		v.addFilter("UnlikelyDeleterious");
	}
	return v
}

def numeric(String s) {
	if (s == null) return 0;
	if (s.equals(".")) return 0;
	return s.toFloat();
}

def isClinical(VCFVariant v) {
	String hgmd_varclass = v.getInfoValue("HC")?:".";
	float dbsnp_clin = numeric(v.getInfoValue("CD"));
	return (hgmd_varclass != "." || (dbsnp_clin > 3 && dbsnp_clin < 8));
}

def isSNPDeleterious(VCFVariant v) {
	if (isClinical(v)) {
		return true;
	}
	String refseq_effect = v.getInfoValue("RFG") ?: ".";
	String ucsc_effect = v.getInfoValue("UCG") ?: ".";
	Pattern protein_altering = ~/nonsynonymous|stop|splic/;
	return (protein_altering.matcher(refseq_effect).find() || protein_altering.matcher(ucsc_effect).find())
}
