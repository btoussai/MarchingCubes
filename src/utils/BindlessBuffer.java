package utils;
import static org.lwjgl.opengl.GL46C.*;
import static org.lwjgl.opengl.NVShaderBufferLoad.*;

import java.nio.ByteBuffer;

public class BindlessBuffer {
	int ID = 0;
	long size = 0;
	int flags = 0;
	long gpu_ptr = 0;

	public BindlessBuffer() {
	
	}
	
	public void makeBindless() {
		long ptr_array[] = new long[1];
		//can take a long time :(
		glGetNamedBufferParameterui64vNV(ID, GL_BUFFER_GPU_ADDRESS_NV, ptr_array);
		gpu_ptr = ptr_array[1];
	
		makeBufferResident(GL_READ_WRITE);
	}
	
	public void makeBufferResident(int access) {
		glMakeNamedBufferResidentNV(ID, access);
	}
	
	public void makeBufferNonResident() {
		glMakeNamedBufferNonResidentNV(ID);
	}
	
	private void reset() {
		glDeleteBuffers(ID);
		int id_array[] = new int[1];
		glCreateBuffers(id_array);
		ID = id_array[0];
	}
	
	public void storeData(long size, int flags) {
		reset();
		glNamedBufferStorage(ID, size, flags);
		this.size = size;
		this.flags = flags;
	
	}
	
	public void storeData(ByteBuffer data, int flags) {
		reset();
		glNamedBufferStorage(ID, data, flags);
		this.size = data.limit();
		this.flags = flags;
	}
	
	public void storeData(int[] data, int flags) {
		reset();
		glNamedBufferStorage(ID, data, flags);
		this.size = data.length * 4;
		this.flags = flags;
	}
	
	public void updateData(ByteBuffer data, long offset) {
		glNamedBufferSubData(ID, offset, data);
	}
	
	public void clearData(int internalformat, int format,
			int type, ByteBuffer data) {
		glClearNamedBufferSubData(ID, internalformat, 0, size, format, type, data);
	}
	
	public long getGPUptr() {
		return gpu_ptr;
	}
	
	public long getSize() {
		return size;
	}
	
	public long getDataSize() {
		return size;
	}
	
	public long[] toBufferHandle() {
		return new long[]{getGPUptr(), getSize()};
	}
	
	public void bindAs(int type) {
		glBindBuffer(type, ID);
	}
	
	public void unbindAs(int type) {
		glBindBuffer(type, 0);
	}
	
	public int getID() {
		return ID;
	}
	
	public void delete() {
		glDeleteBuffers(ID);
		ID = 0;
		size = 0;
		gpu_ptr = 0;
	}
	
}
