package utils;
import static org.lwjgl.opengl.GL46C.*;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.ARBBindlessTexture.*;

import org.joml.Vector3i;


public class Texture3D {
	
	int ID = 0;
	int width = 0;
	int height = 0;
	int depth = 0;
	int internalFormat = 0;
	int format = 0;
	int type = 0;

	long tex_handle = 0;
	long image_handle = 0;
	
	public Texture3D(int width, int height, int depth, 
			int internalFormat, int format, int type, 
			boolean clear, ByteBuffer data)
	{
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.internalFormat = internalFormat;
		this.format = format;
		this.type = type;

		int ID_array[] = new int[1];
		glCreateTextures(GL_TEXTURE_3D, ID_array);
		ID = ID_array[0];
		glTextureStorage3D(ID, 1, internalFormat, width, height, depth);

		if(clear){
			glClearTexImage(ID, 0, format, type, data);
		}else if(data != null){
			glTextureSubImage3D(ID, 0, 0, 0, 0, width, height, depth, format, type, data);
		}

		setFilter(GL_NEAREST, GL_NEAREST);
		setWrapMode(GL_CLAMP_TO_EDGE);
	}
	
	public Texture3D(int width, int height, int depth, 
			int internalFormat, int format, int type, 
			boolean clear, float[] data)
	{
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.internalFormat = internalFormat;
		this.format = format;
		this.type = type;

		int ID_array[] = new int[1];
		glCreateTextures(GL_TEXTURE_3D, ID_array);
		ID = ID_array[0];
		glTextureStorage3D(ID, 1, internalFormat, width, height, depth);

		if(clear){
			glClearTexImage(ID, 0, format, type, data);
		}else if(data != null){
			glTextureSubImage3D(ID, 0, 0, 0, 0, width, height, depth, format, type, data);
		}

		setFilter(GL_NEAREST, GL_NEAREST);
		setWrapMode(GL_CLAMP_TO_EDGE);
	}

	public void delete(){
		glDeleteTextures(ID);
	}
	
	public void bindToTextureUnit(int unit){
		glBindTextureUnit(unit, ID);
	}

	public void unbindFromTextureUnit(int unit){
		glBindTextureUnit(unit, 0);
	}

	public int getID(){
		return ID;
	}

	public Vector3i getDims(){
		return new Vector3i(width, height, depth);
	}

	public int getDepth(){
		return depth;
	}

	public int getHeight(){
		return height;
	}

	public int getWidth(){
		return width;
	}

	public void createBindlessHandles(){
		tex_handle = glGetTextureHandleARB(ID);
		image_handle = glGetImageHandleARB(ID, 0, true, 0, internalFormat);
	}

	long getImageHandle(){
		return image_handle;
	}

	long getTextureHandle(){
		return tex_handle;
	}

	public long[] getHandles(){
		return new long[]{tex_handle, image_handle};
	}

	public void makeTextureHandleResident(boolean resident) {
		if(resident){
			glMakeTextureHandleResidentARB(tex_handle);
		}else{
			glMakeTextureHandleNonResidentARB(tex_handle);
		}
	}

	public void makeImageHandleResident(boolean resident, int access) {
		if(resident){
			glMakeImageHandleResidentARB(image_handle, access);
		}else{
			glMakeImageHandleNonResidentARB(image_handle);
		}
	}

	public int getInternalFormat() {
		return internalFormat;
	}

	public void setFilter(int minFilter, int magFilter) {
		glTextureParameteri(ID, GL_TEXTURE_MIN_FILTER, minFilter);
		glTextureParameteri(ID, GL_TEXTURE_MAG_FILTER, magFilter);
	}

	public void setWrapMode(int wrapMode){
		glTextureParameteri(ID, GL_TEXTURE_WRAP_R, wrapMode);
		glTextureParameteri(ID, GL_TEXTURE_WRAP_S, wrapMode);
		glTextureParameteri(ID, GL_TEXTURE_WRAP_T, wrapMode);
	}

}


