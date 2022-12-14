package ch.discord.mknk;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.HttpClients;

public class ArchiveSharer {

	public static void main(String[] args) throws ParseException, MalformedURLException, IOException, InterruptedException {

		LocalDateTime dtStart = LocalDateTime.now();

		Option userName = Option.builder("u").required().hasArg().longOpt("user-name").build();
		Option userId = Option.builder("id").required().hasArg().longOpt("user-id").build();
		Option file = Option.builder("f").required().hasArg().longOpt("file").build();
		Option ffmpeg = Option.builder("ffm").required().hasArg().longOpt("ffmpeg").build();
		Option staging = Option.builder("stg").required().hasArg().longOpt("staging").build();

		Options options = new Options();
		options.addOption(userName);
		options.addOption(userId);
		options.addOption(file);
		options.addOption(ffmpeg);
		options.addOption(staging);

		CommandLineParser cliParser = new DefaultParser();
		CommandLine cli = cliParser.parse(options, args);

		System.out.println("Adding salt to Metadata ...");
		File inputFile = new File(cli.getOptionValue("f"));
		
		System.out.println("Input file is: "+inputFile.getAbsolutePath());

		String salt = UUID.randomUUID().toString();

		Path outPath = Files.createDirectories(Paths.get(cli.getOptionValue("stg"), salt));
		outPath = outPath.resolve(inputFile.getName());
		
		Path ffmpegPath = Paths.get(cli.getOptionValue("ffm"));
		
		ProcessBuilder ffmpegProcessBuilder = new ProcessBuilder().command(ffmpegPath.toAbsolutePath().toString(), "-i", "\"" + inputFile.getAbsolutePath() + "\"", "-c", "copy", "-movflags",
				"use_metadata_tags", "-map_metadata", "0", "-metadata", "comment=" + salt, "-loglevel", "quiet", "\"" + outPath.toAbsolutePath().toString() + "\"");

//		ffmpegProcessBuilder.command().forEach(System.out::println);

		Process ffmpegProcess = ffmpegProcessBuilder.directory(ffmpegPath.getParent().toFile()).start();
		ffmpegProcess.waitFor(); 

		System.out.println("Salt added to metadata");

		HttpEntity litterBoxRequest = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE).addTextBody("reqtype", "fileupload").addTextBody("time", "1h")
				.addPart("fileToUpload", new FileBody(outPath.toFile())).build();

		HttpPost httpPost = new HttpPost("https://litterbox.catbox.moe/resources/internals/api.php");
		httpPost.setEntity(litterBoxRequest);

		System.out.println("Sending to litter box...");
		HttpClient client = HttpClients.createDefault();
		HttpResponse response = client.execute(httpPost);

//		Arrays.asList(response.getAllHeaders()).forEach(System.out::println);

		String uploadURL = "";

		try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
			uploadURL = br.readLine();
		}

		System.out.println("Got litter box response");

		System.out.println("Deleting salted file");
		Files.delete(outPath);

		LocalDateTime dtEnd = LocalDateTime.now();

		System.out.println("1h Share generated for " + cli.getOptionValue("u") + "(" + cli.getOptionValue("id") + ") with SALT '" + salt + "': " + uploadURL + " (Took "
				+ Duration.between(dtStart, dtEnd).toMillis() + "ms)");

	}
}
