package unimelb.bitbox;


import java.io.FileReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.RSAPublicKey;

import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;


/**
 * Security functions
 * @author Xin Tian
 *
 */
public class Secure {
	
	
	public static PrivateKey loadPrivateKey() {
		String privateKeyFile = "bitboxclient_rsa";
		Security.addProvider(new BouncyCastleProvider());
		
		try {
			PEMParser pemParser;
			pemParser = new PEMParser(new FileReader(privateKeyFile));
			JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
			Object object = pemParser.readObject();
			KeyPair kp = converter.getKeyPair((PEMKeyPair) object);
			PrivateKey privateKey = kp.getPrivate();
			pemParser.close();
			return privateKey;
				
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			return null;
}

		
public static byte[] skGenarate() {	
	byte[] secret_key = new byte[128];
    KeyGenerator kg;
	try {
		kg = KeyGenerator.getInstance("AES");
		kg.init(128);
	    SecretKey sk = kg.generateKey();
	    secret_key = sk.getEncoded();
	} catch (NoSuchAlgorithmException e) {
		e.printStackTrace();
	}
	return secret_key;
}

public static String RSAEncrypt(byte[] content, RSAPublicKey pubKey) throws Exception{
	//RSA encrypt
	Cipher cipher = Cipher.getInstance("RSA");
	cipher.init(Cipher.ENCRYPT_MODE, pubKey);
	String encryption = Base64.getEncoder().encodeToString(cipher.doFinal(content));		
	return encryption;
}

public static byte[] RSADecrypt(String encryption, PrivateKey priKey){
	//RSA decrypt
	Cipher cipher;
	try {
		cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.DECRYPT_MODE, priKey);
		byte[] content = cipher.doFinal(Base64.getDecoder().decode(encryption));
		return content;
	} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (InvalidKeyException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IllegalBlockSizeException | BadPaddingException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return null;
}

public static String AESEncrypt(byte[]keyBytes,String context)  {
		Key key = new SecretKeySpec(keyBytes, "AES");
		Cipher cipher;
		String encryption="";
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(cipher.ENCRYPT_MODE, key);
			encryption = Base64.getEncoder().encodeToString(cipher.doFinal(context.getBytes()));
			return encryption;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
}

public static String AESDecrypt(byte[]keyBytes, String encryption) {
		Key key = new SecretKeySpec(keyBytes, "AES");
		Cipher cipher;
	try {
		
		cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, key);
		byte[] content = cipher.doFinal(Base64.getDecoder().decode(encryption));
		String decryption = new String(content);
		return decryption;
	} catch (NoSuchAlgorithmException e) {
		e.printStackTrace();
	} catch (NoSuchPaddingException e) {
		e.printStackTrace();
	} catch (InvalidKeyException e) {
		e.printStackTrace();
	} catch (IllegalBlockSizeException e) {
		e.printStackTrace();
	} catch (BadPaddingException e) {
		e.printStackTrace();
	}
	return null;
}


}
