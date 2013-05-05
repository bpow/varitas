package org.drpowell.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

public class ChecksummingOutputStream extends FilterOutputStream {
	private final MessageDigest digest;
	private long size = 0;
	private boolean closed = false;
	
	public ChecksummingOutputStream(String algorithm, OutputStream delegate) throws NoSuchAlgorithmException {
		super(delegate);
		digest = MessageDigest.getInstance(algorithm);
	}
	
	public byte [] getDigest() throws IOException {
		close();
		return digest.digest();
	}
	
	public String getDigestString() throws IOException {
		return (new HexBinaryAdapter().marshal(getDigest()).toLowerCase() + ", size: " + size);
	}

	@Override
	public void write(int b) throws IOException {
		digest.update((byte) b);
		size++;
		super.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		digest.update(b, off, len);
		size += len;
		super.write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		super.flush();
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			super.close();
		}
	}

}
