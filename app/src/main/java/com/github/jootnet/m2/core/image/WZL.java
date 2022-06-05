package com.github.jootnet.m2.core.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.github.jootnet.m2.core.SDK;

public final class WZL {
	/** 库内图片总数 */
	private int imageCount;
	/** 纹理数据起始偏移 */
	private int[] offsetList;
	/** 纹理消费者 */
	private TextureConsumer textureConsumer;

	/**
	 * 设置纹理加载完成回调
	 * 
	 * @param consumer 事件处理函数
	 * @return 当前对象
	 */
	public WZL onTextureLoaded(TextureConsumer consumer) {
		textureConsumer = consumer;
		return this;
	}

	private void unpackTextures(byte[] data) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		for (int no = 0; no < imageCount; ++no) {
			if (offsetList[no] == 0) {
				if (textureConsumer != null)
					textureConsumer.recv(no, EMPTY);
				continue;
			}
			byteBuffer.position(offsetList[no]);
			if (byteBuffer.remaining() < 16)
				break;
			byte colorBit = byteBuffer.get();
			boolean compressFlag = byteBuffer.get() != 0;
			byteBuffer.position(byteBuffer.position() + 2); // 2字节未知数据
			short width = byteBuffer.getShort();
			short height = byteBuffer.getShort();
			short offsetX = byteBuffer.getShort();
			short offsetY = byteBuffer.getShort();
			int dataLen = byteBuffer.getInt();
			if (dataLen == 0) {
				// 这里可能是一个bug，或者是其他引擎作者没有说清楚
				// 本来一直以为是compressFlag作为是否zlib压缩的标志位
				// 后来发现如果这里的长度是0，则表示没有压缩，后面是图片尺寸的裸数据
				dataLen = width * height;
				if (colorBit == 5) dataLen *= 2;
				compressFlag = false;
			}
			if (byteBuffer.remaining() < dataLen)
				break;
			byte[] pixels = new byte[dataLen];
			byteBuffer.get(pixels);
			if (compressFlag) {
				try {
					pixels = SDK.unzip(pixels);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			byte[] sRGBA = new byte[width * height * 4];
			if (colorBit != 5) { // 8位
				int p_index = 0;
				for (int h = height - 1; h >= 0; --h)
					for (int w = 0; w < width; ++w) {
						// 跳过填充字节
						if (w == 0)
							p_index += SDK.skipBytes(8, width);
						if (p_index >= pixels.length) break;
						byte[] pallete = SDK.palletes[pixels[p_index++] & 0xff];
						int _idx = (w + h * width) * 4;
						sRGBA[_idx + 3] = pallete[1];
						sRGBA[_idx + 2] = pallete[2];
						sRGBA[_idx + 1] = pallete[3];
						sRGBA[_idx] = pallete[0];
					}
			} else { // 16位
				ByteBuffer bb = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
				int p_index = 0;
				for (int h = height - 1; h >= 0; --h)
					for (int w = 0; w < width; ++w, p_index += 2) {
						// 跳过填充字节
						if (w == 0)
							p_index += SDK.skipBytes(16, width);
						if (p_index >= pixels.length) break;
						short pdata = bb.getShort(p_index);
						byte r = (byte) ((pdata & 0xf800) >> 8);// 由于是与16位做与操作，所以多出了后面8位
						byte g = (byte) ((pdata & 0x7e0) >> 3);// 多出了3位，在强转时前8位会自动丢失
						byte b = (byte) ((pdata & 0x1f) << 3);// 少了3位
						int _idx = (w + h * width) * 4;
						sRGBA[_idx + 1] = b;
						sRGBA[_idx + 2] = g;
						sRGBA[_idx + 3] = r;
						if (r == 0 && g == 0 && b == 0) {
							sRGBA[_idx] = 0;
						} else {
							sRGBA[_idx] = -1;
						}
					}
			}
			if (textureConsumer != null) {
				textureConsumer.recv(no, new Texture(no, false, width, height, offsetX, offsetY, sRGBA));
			}
		}
	}

	@FunctionalInterface
	public interface TextureConsumer {
		/**
		 * 单个纹理加载完毕时触发
		 * 
		 * @param no  纹理编号，从0开始
		 * @param tex 纹理对象
		 */
		void recv(int no, Texture tex);
	}

	/** 空图片 */
	private static Texture EMPTY;
	
	static {
		EMPTY = new Texture(-1, true, 1, 1, 0, 0, new byte[] { SDK.palletes[0][1], SDK.palletes[0][2], SDK.palletes[0][3], SDK.palletes[0][0] });
	}
	
	public void feedWzx(byte[] wzxData) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(wzxData).order(ByteOrder.LITTLE_ENDIAN);
		byteBuffer.position(44);
		imageCount = byteBuffer.getInt();
		offsetList = new int[imageCount + 1];
		for (int i = 0; i < imageCount; ++i) {
			offsetList[i] = byteBuffer.getInt();// UnsignedInt
			if (offsetList[i] < 64) offsetList[i] = 0;
		}
	}
	
	public void feedWzl(byte[] wzlData) {
		unpackTextures(wzlData);
	}
}
