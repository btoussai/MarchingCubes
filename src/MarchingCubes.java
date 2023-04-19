import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_REPEAT;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_READ_ONLY;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL31C.GL_RGBA16_SNORM;
import static org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.glBindImageTexture;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.glMapNamedBuffer;
import static org.lwjgl.opengl.GL45C.glUnmapNamedBuffer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joml.Matrix4f;
import org.joml.RoundingMode;
import org.joml.Vector3f;
import org.joml.Vector3i;

import imgui.ImGui;
import utils.BindlessBuffer;
import utils.QueryBuffer;
import utils.Shader;
import utils.Texture3D;
import utils.VAO;
import utils.VBO;

public class MarchingCubes {
	BindlessBuffer LUT;

	BindlessBuffer marchingCubesOutput = new BindlessBuffer();
	Texture3D noiseTexture;

	BindlessBuffer Atomic;
	Shader marchingCubesShader;
	Shader generateShader;

	int maxVertices[] = new int[] { 500 };
	int volumeResolution[] = new int[] { 16 };
	float baseFrequency[] = new float[] { 0.02f };
	float baseAmplitude[] = new float[] { 3.0f };

	final int vertexSize = 4 * 4;

	int generatedVertices = 0;

	enum Operations {
		GenerateVolume, ExtractMesh
	}

	Map<Operations, QueryBuffer> timers = Arrays.stream(Operations.values())
			.collect(Collectors.toMap(e -> e, e -> new QueryBuffer(GL_TIME_ELAPSED, 10)));

	public MarchingCubes() {
		marchingCubesShader = new Shader("resources/shaders/marchingCubes.cs");
		marchingCubesShader.finishInit();
		marchingCubesShader.init_uniforms(List.of("SDF_OFFSET", "transform", "chunk"));

		generateShader = new Shader("resources/shaders/generateTerrain.cs");
		generateShader.finishInit();
		generateShader.init_uniforms(List.of("chunk", "baseFrequency", "baseAmplitude"));

		prepareMarchingCubesBuffer();
		prepareVolumeTexture();

		// Make a bigger buffer whose every 16 element is the number of triangles to
		// generate
		int buff[] = new int[256 + 3 * 5 * 256];
		for (int i = 0; i < 256; i++) {
			int triangles = 0;
			for (int j = 0; j < 5; j++) {
				if (MarchingCubesLUT.EdgesLUT[i][j][0] == -1) {
					break;
				}
				triangles++;
			}

			for (int j = 0; j < 5; j++) {
				for (int k = 0; k < 3; k++) {
					buff[16 * i + 3 * j + k + 1] = MarchingCubesLUT.EdgesLUT[i][j][k];
				}
			}
			buff[i * 16] = triangles;
		}

		LUT = new BindlessBuffer();
		LUT.storeData(buff, GL_MAP_READ_BIT);

		prepareNoiseTexture();
	}

	void prepareMarchingCubesBuffer() {
//		System.out.println("Creating vertex buffer with a capacity of " + maxVertices[0] + "K vertices.");
		marchingCubesOutput.storeData(maxVertices[0] * 1000 * vertexSize, GL_MAP_READ_BIT);
	}

	Texture3D prepareVolumeTexture() {
		int s = volumeResolution[0];
		return new Texture3D(s, s, s, GL_RGBA16_SNORM, GL_RGBA, GL_FLOAT, true, (ByteBuffer) null);
	}

	public void prepareNoiseTexture() {
		int s = 16;
		if (noiseTexture != null)
			noiseTexture.delete();

		float array[] = new float[4 * s * s * s];
		for (int k = 0; k < array.length; k++) {
			array[k] = (float) Math.random() * 2.0f - 1.0f;
		}

		noiseTexture = new Texture3D(s, s, s, GL_RGBA16_SNORM, GL_RGBA, GL_FLOAT, false, array);
		noiseTexture.setFilter(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR);
		noiseTexture.setWrapMode(GL_REPEAT);
	}

	void updateGUI() {
		ImGui.sliderFloat("Base Frequency", baseFrequency, 0, 0.1f);
		ImGui.sliderFloat("Base Amplitude", baseAmplitude, 0, 1);

		if (ImGui.treeNode("Marching Cubes")) {

			if (ImGui.sliderInt("Max Vertices (k)", maxVertices, 10, 1000)) {
				prepareMarchingCubesBuffer();
			}
			if (ImGui.sliderInt("Volume Resolution", volumeResolution, 16, 256)) {
				prepareVolumeTexture();
			}

			ImGui.text(String.format("Generate Volume: %.02f ms",
					timers.get(Operations.GenerateVolume).getLastResult(true) * 1.0E-6f));
			ImGui.text(String.format("Extract Mesh: %.02f ms",
					timers.get(Operations.ExtractMesh).getLastResult(true) * 1.0E-6f));

			ImGui.treePop();
		}
	}
	
	public Texture3D fillChunkTexture(Vector3f chunk) {
		Texture3D volumeTexture = prepareVolumeTexture();
		final int groups = (volumeTexture.getWidth() + 3) / 4;

		var q = timers.get(Operations.GenerateVolume).push_back();
		q.begin();
		generateShader.start();
		generateShader.loadVec3("chunk", chunk);
		generateShader.loadFloat("baseFrequency", baseFrequency[0]);
		generateShader.loadFloat("baseAmplitude", baseAmplitude[0]);
		glBindImageTexture(0, volumeTexture.getID(), 0, true, 0, GL_WRITE_ONLY, GL_RGBA16_SNORM);
		noiseTexture.bindToTextureUnit(1);
		glDispatchCompute(groups, groups, groups);
		noiseTexture.unbindFromTextureUnit(1);
		generateShader.stop();
		q.end();

		glMemoryBarrier(GL_ALL_BARRIER_BITS);
		return volumeTexture;
	}

	public void extractMesh(Chunk chunk) {
		final int groups = (chunk.voxels.getWidth() + 3) / 4;

		int zero[] = new int[] { 0, 0, 0 };
		Atomic = new BindlessBuffer();
		Atomic.storeData(zero, GL_MAP_READ_BIT);

		var q = timers.get(Operations.ExtractMesh).push_back();
		q.begin();
		marchingCubesShader.start();
		marchingCubesShader.loadFloat("SDF_OFFSET", 0.0f);
		marchingCubesShader.loadMat4("transform", new Matrix4f());
		marchingCubesShader.loadVec3("chunk", new Vector3f(chunk.coords));
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, Atomic.getID());
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, marchingCubesOutput.getID());
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, LUT.getID());
		glBindImageTexture(0, chunk.voxels.getID(), 0, true, 0, GL_READ_ONLY, GL_RGBA16_SNORM);
		glDispatchCompute(groups, groups, groups);
		glMemoryBarrier(GL_ALL_BARRIER_BITS);
		marchingCubesShader.stop();
		q.end();

		IntBuffer buff = glMapNamedBuffer(Atomic.getID(), GL_READ_ONLY).asIntBuffer();
		generatedVertices = buff.get(0);
		int emptyVoxels = buff.get(1);
		int filledVoxels = buff.get(2);
		glUnmapNamedBuffer(Atomic.getID());
		Atomic.delete();

		int capacity = Math.min(generatedVertices, maxVertices[0] * 1000) * vertexSize;
		if(chunk.vao != null) {
			chunk.vao.delete();
			chunk.vao = null;
			chunk.vertices_array = null;
		}

		chunk.vertices = capacity / vertexSize;
		if (capacity > 0) {
			chunk.vao = new VAO();
			chunk.vao.bind();
			VBO vbo = new VBO(GL_ARRAY_BUFFER);
			vbo.bind();
			vbo.reserveData(capacity, GL_STATIC_DRAW);
			glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, 0);
			vbo.unbind();
			chunk.vao.unbind();
			chunk.vao.getAttributeVBOs().add(vbo);
			glCopyNamedBufferSubData(marchingCubesOutput.getID(), vbo.getID(), 0, 0, capacity);
			
			FloatBuffer MCOuput = glMapNamedBuffer(marchingCubesOutput.getID(), GL_READ_ONLY).asFloatBuffer();
			chunk.vertices_array = new float[chunk.vertices * 3];
			for(int i=0; i<chunk.vertices; i++) {
				chunk.vertices_array[3*i+0] = MCOuput.get(4*i+0);
				chunk.vertices_array[3*i+1] = MCOuput.get(4*i+1);
				chunk.vertices_array[3*i+2] = MCOuput.get(4*i+2);
			}
			glUnmapNamedBuffer(marchingCubesOutput.getID());
			
		}

	}

}
