/**
 * 
 */
package xextension.autoupdate;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import xextension.global.ConfigHelper;
import xextension.global.Configurations;

/**
 * @author QiaoMingkui
 * 
 */
public class AutoUpdater {
	private static final Logger logger = LogManager.getLogger(AutoUpdater.class);

	/**
	 * auto update
	 * 
	 * @throws IOException 
	 */
	public void autoUpdate() throws IOException {
		String[] t = loadUpdateLog();

		if (t == null) {
			logger.warn("Update failed, can not load update log");
		} else {
			String server = t[0];
			String updateLog = t[1];
			logger.info("Load update log completely");
			autoUpdate(server, updateLog);
		}
	}

	private void autoUpdate(String server, String updateLog) throws IOException {
		// current version
		String currentVersion = ConfigHelper.getProperty(Configurations.CUSTOMER_VERSION);
		logger.info("current version: " + currentVersion);

		// backup files before updating
		String backupFolder = "" + System.currentTimeMillis() + (int) (Math.random() * 10000);
		// newest version
		String newestVersion = null;

		try {
			// update according to updatelog
			String line;
			String versionToBeUpdated = null;
			StringTokenizer lines = new StringTokenizer(updateLog);
			Map<String, Boolean> updatedFiles = new HashMap<String, Boolean>();
			while (lines.hasMoreTokens()) {
				line = lines.nextToken().trim();
				if (line.length() == 0) {
					continue;
				}

				// version name
				if (line.startsWith(Configurations.UPDATE_LOG_VERSION_PREFIX) && line.endsWith(Configurations.UPDATE_LOG_VERSION_SUFFIX)) {
					versionToBeUpdated = line.substring(1, line.length() - 1).trim();
					if (isNewerVersion(versionToBeUpdated, currentVersion)) {
						logger.info("Find a newer version to be updated: " + versionToBeUpdated);
						if (newestVersion == null) {
							newestVersion = versionToBeUpdated;
						}
					} else { // all newer versions have been updated
						break;
					}
				} else if (versionToBeUpdated != null) {
					// already has a newer patch which has been updated
					if (updatedFiles.containsKey(line)) {
						continue;
					}

					// backup before updating if need
					backupFile(line, backupFolder);
					// update from server
					updateFile(line, versionToBeUpdated, server);
					// flag the file has been updated
					updatedFiles.put(line, true);
				}
			}

			// save current version after update completed
			if (newestVersion != null) {
				ConfigHelper.setProperty(Configurations.CUSTOMER_VERSION, newestVersion);
			}
		} catch (IOException e) {
			logger.warn("update failed, restore backup files");
			restoreBackupFiles(backupFolder);
			throw e;
		} finally {
			if (newestVersion != null) {
				logger.info("delete backup files in " + backupFolder);
				deleteBackupFolder(backupFolder);
			}
		}
	}

	private void updateFile(String path, String version, String server) throws IOException {
		logger.info("Update file: " + path);

		File file = new File(path);
		createFile(file);

		File configFile = new File(ConfigHelper.CONFIG_FILE);
		Properties config = null;
		if (configFile.compareTo(file) == 0) {
			config = ConfigHelper.readConfig();
		}

		HttpURLConnection connection = null;
		BufferedInputStream inStream = null;
		FileOutputStream outStream = null;
		try {
			connection = (HttpURLConnection) new URL(
						server + Configurations.UPDATE_PATCHES_DIR + version + Configurations.UPDATE_LOG_FILE_SEPARATOR + path
					).openConnection();
			connection.connect();
			inStream = new BufferedInputStream(connection.getInputStream());
			outStream = new FileOutputStream(file);
			writeData(inStream, outStream);

		} finally {
			if (connection != null) {
				connection.disconnect();
			}
			if (inStream != null) {
				inStream.close();
			}
			if (outStream != null) {
				outStream.close();
			}
		}

		// restore customer config
		if (config != null) {
			Properties newConfig = ConfigHelper.readConfig();

			Set<Entry<Object, Object>> props = config.entrySet();
			String name;
			for (Entry<Object, Object> prop : props) {
				name = (String) prop.getKey();
				if (name.startsWith(Configurations.CUSTOMER_PREFIX)) {
					newConfig.setProperty(name, (String) prop.getValue());
				}
			}

			ConfigHelper.writeConfig(newConfig);
		}
	}

	private void backupFile(String filePath, String backupFolder) throws IOException {
		// this file does not exist
		File file = new File(filePath);
		File backupFile = new File(backupFolder + File.separator + filePath);
		// each file only backup once
		if (!file.exists() || backupFile.exists()) {
			return;
		}

		logger.info("backup file: " + filePath);
		InputStream inStream = null;
		FileOutputStream outStream = null;
		try {
			createFile(backupFile);
			inStream = new FileInputStream(file);
			outStream = new FileOutputStream(backupFile);
			writeData(inStream, outStream);
		} finally {
			if (inStream != null) {
				inStream.close();
			}
			if (outStream != null) {
				outStream.close();
			}
		}
	}

	private void createFile(File file) throws IOException {
		if (file.exists()) {
			return;
		}

		File parent = file.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		file.createNewFile();
	}

	private void deleteBackupFolder(String folderPath) {
		File folder = new File(folderPath);
		if (!folder.exists()) {
			return;
		}

		File[] subFiles = folder.listFiles();
		for (File subFile : subFiles) {
			try {
				if (subFile.isDirectory()) {
					deleteBackupFolder(subFile.getPath());
				} else {
					subFile.delete();
				}
			} catch (Exception e) {
			}
		}
		folder.delete();
	}

	private void restoreBackupFiles(String folderPath) {
		try {
			File folder = new File(folderPath);
			if (!folder.exists()) {
				return;
			}

			File[] subFiles = folder.listFiles();
			InputStream inStream = null;
			FileOutputStream outStream = null;
			File restoreFile;
			for (File subFile : subFiles) {
				try {
					if (subFile.isDirectory()) {
						restoreBackupFiles(subFile.getPath());
					} else {
						restoreFile = new File(folderPath.substring(folderPath.indexOf(File.separator) + 1));
						inStream = new FileInputStream(subFile);
						outStream = new FileOutputStream(restoreFile);
						writeData(inStream, outStream);
					}
				} catch (Exception e) {
				} finally {
					if (inStream != null) {
						inStream.close();
					}
					if (outStream != null) {
						outStream.close();
					}
				}
			}
		} catch (Exception e) {
		}
	}

	private void writeData(InputStream inStream, FileOutputStream outStream) throws IOException {
		byte[] buffer = new byte[128 * 1024];
		int countRead = 0;
		while ((countRead = inStream.read(buffer)) != -1) {
			outStream.write(buffer, 0, countRead);
		}
	}

	private boolean isNewerVersion(String versionToBeUpdated, String currentVersion) {
		String[] vers1 = versionToBeUpdated.split(Configurations.VERSION_NAME_SEPARATOR);
		String[] vers2 = currentVersion.split(Configurations.VERSION_NAME_SEPARATOR);
		int len = Math.min(vers1.length, vers2.length);
		int verNo1, verNo2;
		try {
			for (int i = 0; i < len; i++) {
				verNo1 = Integer.parseInt(vers1[i]);
				verNo2 = Integer.parseInt(vers2[i]);
				if (verNo1 > verNo2) {
					return true;
				}
			}
			return vers1.length > vers2.length;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private String[] loadUpdateLog() throws IOException {
		String customer = ConfigHelper.getProperty(Configurations.CUSTOMER_AUTOUPDATE_SERVER);
		if (customer != null && customer.trim().length() != 0) {
			logger.info("Use customer address to load update log: " + customer);
			String updateLog = loadUpdateLogFromURL(customer);
			return new String[] { customer, updateLog };
		} else {
			String defaultServers = ConfigHelper.getProperty(Configurations.AUTOUPDATE_DEFAULT_SERVERS);
			logger.info("Customer address is not specified, use default instead: " + defaultServers);
			return loadUpdateLogFromDefault(defaultServers);
		}
	}

	private String[] loadUpdateLogFromDefault(String addressStr) {
		String[] defaultAddresses = addressStr.split(";");
		String updateLog;
		for (String address : defaultAddresses) {
			try {
				updateLog = loadUpdateLogFromURL(address);
				return new String[] { address, updateLog };
			} catch (IOException e) {
				// ignore
			}
		}

		return null;
	}

	private String loadUpdateLogFromURL(String server) throws IOException {
		final String line_separator = "\n";
		HttpURLConnection connection = null;
		BufferedReader reader = null;
		try {
			connection = (HttpURLConnection) new URL(server + Configurations.UPDATE_LOG_PATH).openConnection();
			connection.connect();
			reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), Configurations.DEFAULT_ENCODING));
			StringBuilder lines = new StringBuilder(4 * 1024);
			String line;
			while ((line = reader.readLine()) != null) {
				lines.append(line).append(line_separator);
			}

			return lines.toString();
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
			if (reader != null) {
				reader.close();
			}
		}
	}

}
