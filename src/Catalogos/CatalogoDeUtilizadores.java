package Catalogos;

import javax.crypto.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 *
 * @author Afonso Santos - FC56368
 * @author Alexandre Figueiredo - FC57099
 * @author Raquel Domingos - FC56378
 *
 */
public class CatalogoDeUtilizadores extends Catalogo {
	private static CatalogoDeUtilizadores INSTANCE = null;
	private static final String USERS_FILE = "serverFiles/users.cif";
	private File users;
	private Map<String, String> registeredUsers;
	private SecretKey secretKey;
	private AlgorithmParameters params;
	
	private CatalogoDeUtilizadores() {
		users = new File(USERS_FILE);
		registeredUsers = new HashMap<>();
		
		try {
			users.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static CatalogoDeUtilizadores getInstance()	{
		if (INSTANCE == null) {
			INSTANCE = new CatalogoDeUtilizadores();
		}
		return INSTANCE;
	}
	
	public void registerUser(String user, String certificateFileName) {
		String toEncrypt = user + SEPARATOR + certificateFileName + "\n";
		byte[] data = null;
		byte[] decryptedData = null;
		byte[] encryptedBytes = null;
		
		//Read the data that was previously on the file
		data = getFileData(users);
		
		if (data.length > 0) { //time to decrypt
			//Get the parameters
			//Now decrypt the data
			decryptedData = decryptData(data);
			//At this point, the contents of the users files are decrypted
			
			//Lets merge the previous contents of the file with this new line
			byte[] toEncryptBytes = toEncrypt.getBytes();
			byte[] mergedContents = new byte[decryptedData.length + toEncryptBytes.length];
			int offset = 0;
			for (int i = 0; i < decryptedData.length; i++) {
				mergedContents[offset] = decryptedData[i];
				offset++;
			}
			for (int i = 0; i < toEncryptBytes.length; i++) {
				mergedContents[offset] = toEncryptBytes[i];
				offset++;
			}
			
			//At this point, I have all the content of the file in a new byte[], ready to be encrypted and write
			try {
				Cipher c = Cipher.getInstance("PBEWithHmacSHA256AndAES_128");
				c.init(Cipher.ENCRYPT_MODE, secretKey, params);
				encryptedBytes = c.doFinal(mergedContents);
			} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			}
			
			//Now, just write the new encrypted contents to the file
			writeToFile(encryptedBytes, users);
		}
		else { //There is nothing on the users file, just encrypt this data and write
			try {
				Cipher c = Cipher.getInstance("PBEWithHmacSHA256AndAES_128");
				c.init(Cipher.ENCRYPT_MODE, secretKey, params);
				encryptedBytes = c.doFinal(toEncrypt.getBytes());
			} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			}
			
			//Now, just write the new encrypted contents to the file
			writeToFile(encryptedBytes, users);
		}		
		registeredUsers.put(user, certificateFileName);
	}
	
	public boolean userExists(String user) {
		return registeredUsers.containsKey(user);
	}
	
	public String getCertificateFileName(String userId) {
		return registeredUsers.get(userId);
	}

	@Override
	public void load() {
		//First, get all the content of the users file
		byte[] data = null;
		byte[] dataParams = null;
		byte[] decryptedData = null;
		
		dataParams = getFileData(new File("serverFiles/params.txt"));
		if (dataParams.length > 0) {
			try {
				params = AlgorithmParameters.getInstance("PBEWithHmacSHA256AndAES_128");
				params.init(dataParams);
			} catch (NoSuchAlgorithmException | IOException e) {
				e.printStackTrace();
			}
		}
		else {
			try {
				Cipher c = Cipher.getInstance("PBEWithHmacSHA256AndAES_128");
				c.init(Cipher.ENCRYPT_MODE, secretKey);
				dataParams = c.getParameters().getEncoded();
				params = AlgorithmParameters.getInstance("PBEWithHmacSHA256AndAES_128");
				params.init(dataParams);
				writeToFile(dataParams, new File("serverFiles/params.txt"));
			} catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
				e.printStackTrace();
			}
		}
		data = getFileData(users);
		
		if (data.length > 0) { //decrypt!
			//Get the parameters
			//decrypt the data
			decryptedData = decryptData(data);
			System.out.println(new String(decryptedData));
			String[] lines = new String(decryptedData).split("\n");
			for (String line : lines) {
				String[] tokens = line.split(SEPARATOR);
				registeredUsers.put(tokens[0],tokens[1]);
			}
		}
	}
	
	private byte[] decryptData(byte[] toDecrypt) {
		byte[] decrypted = null;
		try {
			Cipher d = Cipher.getInstance("PBEWithHmacSHA256AndAES_128");
			d.init(Cipher.DECRYPT_MODE, secretKey, params);
			decrypted = d.doFinal(toDecrypt);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
		}
		return decrypted;
	}
	
	private void writeToFile(byte[] data, File file) {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private byte[] getFileData(File file) {
		byte[] data = null;
		try {
			FileInputStream fis = new FileInputStream(file);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buffer = new byte[16];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				bos.write(buffer, 0, bytesRead);
			}
			fis.close();
			bos.close();
			data = bos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}

	public SecretKey getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(SecretKey secretKey) {
		this.secretKey = secretKey;
	}
}
