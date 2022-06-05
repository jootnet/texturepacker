package texturepacker;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.github.jootnet.m2.core.SDK;
import com.github.jootnet.m2.core.image.WZL;

public class WZLImagePacker {

	public static void main(String[] args) throws IOException {
		var mirtDataDir = "D:\\Legend of mirt\\data";
		var mirtDataPackedOutDir = "D:\\mirt";
		var wzxs = new ArrayList<String>();
		
		Files.list(Paths.get(mirtDataDir)).forEach(p -> {
			var fpath = p.toFile().getAbsolutePath();
			if (fpath.endsWith(".wzx") && Files.exists(Paths.get(SDK.changeFileExtension(fpath, ".wzl")))) {
				wzxs.add(fpath);
			}
		});
		
		wzxs.parallelStream().forEach(wzx -> {
			var ilName = SDK.changeFileExtension(new File(wzx).getName(), "").toLowerCase();
			var outDir = new File(mirtDataPackedOutDir, ilName);
			if (outDir.exists()) return;
			var wzl = new WZL();
			File tmpDir = null;
			try {
				tmpDir = Files.createTempDirectory(null).toFile();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return;
			}
			final File inDir = tmpDir;
			var offsets = new HashMap<String, Map<String, Integer>>();
			TexturePacker.Settings settings = new TexturePacker.Settings();
			wzl.onTextureLoaded((i, tex) -> {
				if (tex.isEmpty) return;
				while (tex.width > settings.maxWidth - settings.paddingX * 2) {
					settings.maxWidth *= 2;
					settings.maxHeight = settings.maxWidth;
				}
				while (tex.height > settings.maxHeight - settings.paddingY * 2) {
					settings.maxHeight *= 2;
					settings.maxWidth = settings.maxHeight;
				}
				var bi = new BufferedImage(tex.width, tex.height, BufferedImage.TYPE_4BYTE_ABGR);
				var pixels = ((DataBufferByte)bi.getRaster().getDataBuffer()).getData();
				System.arraycopy(tex.pixels, 0, pixels, 0, pixels.length);
				try {
					ImageIO.write(bi, "png", new File(inDir, i + ".png"));
				} catch (IOException e) {
					e.printStackTrace();
				}
				var offset = new HashMap<String, Integer>();
				offset.put("x", tex.offsetX);
				offset.put("y", tex.offsetY);
				offsets.put(i + "", offset);
			});
			try {
				wzl.feedWzx(Files.readAllBytes(Paths.get(wzx)));
				wzl.feedWzl(Files.readAllBytes(Paths.get(SDK.changeFileExtension(wzx, ".wzl"))));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			if (offsets.isEmpty()) return;
			outDir.mkdir();
			TexturePacker.process(settings, tmpDir.toString(), outDir.toString(), ilName);
			try {
				var lines = Files.readAllLines(Paths.get(outDir.toString(), ilName + ".atlas"));
				for (var i = 0; i < lines.size(); ++i) {
					var line = lines.get(i);
					if (line != null && line.contains("offset:")) {
						for (var j = i - 1; j > 0; --j) {
							line = lines.get(j);
							if (!line.contains(":")) {
								lines.set(i, "  offset: " + offsets.get(line).get("x") + ", " + offsets.get(line).get("y"));
								break;
							}
						}
					}
				}
				Files.write(Paths.get(outDir.toString(), ilName + ".atlas"), lines);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		});
	}

}
