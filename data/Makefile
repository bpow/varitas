.PHONY: all

.SECONDARY: dbNSFP2.0b3.zip dbNSFP1.3.zip ESP6500.snps.vcf.tar.gz

all: TGwMAF ncbiSNP137 CG54 ESP6500 dbNSFP2.0b3
	@echo WARNING-- not making target niehs95 because it requires manual downloads

TGwMAF: ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.dllog

ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz:
	$(eval $@_URL:=http://ftp-trace.ncbi.nih.gov/1000genomes/ftp/release/20110521/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz)
	echo "URL: $($@_URL)" >> ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.dllog
	curl -O "$($@_URL)"

ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz.tbi:
	@echo Using externally-generated tabix index rather than making our own
	$(eval $@_URL:=http://ftp-trace.ncbi.nih.gov/1000genomes/ftp/release/20110521/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz.tbi)
	echo "URL: $($@_URL)" >> ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.dllog
	curl -O "$($@_URL)"

ncbiSNP137: ncbiSNP137.vcf.dllog

ncbiSNP137.vcf.gz:
	$(eval $@_URL:=ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/00-All.vcf.gz)
	echo "URL: $($@_URL)" >> ncbiSNP137.vcf.dllog
	curl -C - -o $@ "$($@_URL)"

ncbiSNP137.vcf.gz.tbi:
	@echo Using externally-generated tabix index rather than making our own
	$(eval $@_URL:=ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/00-All.vcf.gz.tbi)
	echo "URL: $($@_URL)" >> ncbiSNP137.vcf.dllog
	curl -C - -o $@ "$($@_URL)"

CG54: Complete_Public_Genomes_54genomes_VQHIGH.vcf.dllog

# FIXME- this may not be the best file to use... The cut command limits extraneous data.
Complete_Public_Genomes_54genomes_VQHIGH.vcf.gz:
	$(eval $@_URL:=ftp://ftp2.completegenomics.com/Multigenome_summaries/Complete_Public_Genomes_54genomes_B37_mkvcf.vcf.bz2)
	echo "URL: $($@_URL)" >> Complete_Public_Genomes_54genomes_VQHIGH.vcf.dllog
	curl "$($@_URL)" | bzcat | cut -f 1-8 | bgzip -c > $@

# information at http://evs.gs.washington.edu
niehs95: niehs95.indels.vcf.dllog niehs95.snps.vcf.dllog

niehs95.indels.vcf.gz: niehs.95samples.polymorphic.filtered.indels.vcf.gz
	gunzip -c < $< | bgzip -c > $@

niehs95.snps.vcf.gz: niehs.95samples.polymorphic.filtered.snps.vcf.gz
	gunzip -c < $< | bgzip -c > $@

niehs.95samples.polymorphic.filtered.indels.vcf.gz niehs.95samples.polymorphic.filtered.snps.vcf.gz:
	@echo --------------------------------------------------------------
	@echo This file must be downloaded manually using the Aspera client:
	@echo $@
	@echo
	@echo See http://evs.gs.washington.edu for more info
	@echo --------------------------------------------------------------
	@exit 1

ESP6500: ESP6500.vcf.dllog

ESP6500.vcf.gz:
	$(eval $@_URL:=http://evs.gs.washington.edu/evs_bulk_data/ESP6500.snps.vcf.tar.gz)
	echo "URL: $($@_URL)" >> ESP6500.vcf.dllog
	curl -O "$($@_URL)"
	TMPDIR=$(shell mktemp -d) && \
	tar -C $$TMPDIR xzf ESP6500.snps.vcf.tar.gz && \
	for chrom in `seq 1 22` X ; do \
		cat "$$TMPDIR/ESP6500.chr$${chrom}.snps.vcf"; \
	done | bgzip -c > $@ && \
	rm -rf $$TMPDIR

dbNSFP2.0b3: dbNSFP2.0b3_variant.dllog

dbNSFP2.0b3.zip:
	$(eval $@_URL:=http://dbnsfp.houstonbioinformatics.org/dbNSFPzip/dbNSFP2.0b3.zip)
	echo "URL: $($@_URL)" >> dbNSFP2.0b3_variant.dllog
	curl -A "UA" -O "$($@_URL)"
	echo "md5sum of download:" >> dbNSFP2.0b3_variant.dllog
	md5sum $@ >> dbNSFP2.0b3_variant.dllog

dbNSFP2.0b3_variant.gz: dbNSFP2.0b3.zip
	for chrom in `seq 1 22` X Y; do \
		unzip -p $< dbNSFP2.0b3_variant.chr$$chrom | tr -d \\r ; \
	done | bgzip -c > $@

dbNSFP1.3.zip:
	$(eval $@_URL:=http://dbnsfp.houstonbioinformatics.org/dbNSFPzip/dbNSFP1.3.zip)
	echo "URL: $($@_URL)" >> dbNSFP1.3.dllog
	curl -A "UA" -O "$($@_URL)"
	echo "md5sum of download:" >> dbNSFP1.3.dllog
	md5sum $@ >> dbNSFP1.3.dllog

dbNSFP1.3.gz: dbNSFP1.3.zip
	TMPDIR=$(shell mktemp -d) && \
	unzip -a -d "$$TMPDIR" $< && \
	for chrom in `seq 1 22` X Y; do \
		tr -d \\r <$$TMPDIR/dbNSFP1.3.chr$$chrom | sort -n -k2 -S1G ; \
	done | bgzip -c > $@ && \
	rm -rf $$TMPDIR

dbNSFP1.3.gz.tbi: dbNSFP1.3.gz
	tabix -s1 -b2 -e2 $<

dbNSFP1.3: dbNSFP1.3.dllog

%.vcf.gz.tbi: %.vcf.gz
	tabix -p vcf $<

%.dllog: %.gz %.gz.tbi
	md5sum $^ >> $@
	date >> $@
	echo '------------' >> $@
