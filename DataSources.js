TGwMAF = {
	DataURL: 'ftp://ftp-trace.ncbi.nih.gov/1000genomes/ftp/release/20110521/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz',
	IndexURL: 'ftp://ftp-trace.ncbi.nih.gov/1000genomes/ftp/release/20110521/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz.tbi',
	PostProcess: 'checkHash'
}

dbSNP137 = {
	DataURL: 'ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/00-All.vcf.gz',
	IndexURL: 'ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/00-All.vcf.gz.tbi',
	PostProcess: 'checkHash'
}

CG54 = {
	DataURL: 'ftp://ftp2.completegenomics.com/Multigenome_summaries/Complete_Public_Genomes_54genomes_B37_mkvcf.vcf.bz2',
	PostProcess: function (is) {
		bzip2is = org.apache.tools.bzip2.CBZip2InputStream (is);
		//TODO - need to bgzip, index
	}
}

ESP6500 = {
	DataURL: http://evs.gs.washington.edu/evs_bulk_data/ESP6500.snps.vcf.tar.gz
	PostProcess: function (is) {
		//TODO untgz, combine, bgzip, index
	}
}

EGP = {
	DataURL: 'file:///'
	PostProcess: function (is) {
		//TODO gunzip, combine snp/indel, bgzip, index
	}
}

dbNSFP = {
	DataURL: 'http://dbnsfp.houstonbioinformatics.org/dbNSFPzip/dbNSFP2.0b3.zip'
	PostProcess: function (is) {
		//TODO unzip, combine, bgzip, index
	}
}

