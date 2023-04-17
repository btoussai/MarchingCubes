import utils.BindlessBuffer;
import utils.QueryBuffer;
import utils.Shader;
import utils.VAO;
import utils.VBO;
import utils.Texture3D;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL46C.*;
import static org.lwjgl.glfw.GLFW.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.RoundingMode;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector4f;

import imgui.ImGui;

public class MarchingCubes {
	BindlessBuffer LUT;

	BindlessBuffer marchingCubesOutput = new BindlessBuffer();
	Texture3D volumeTexture;
	Texture3D noiseTexture;
	
	BindlessBuffer Atomic;
	Shader marchingCubesShader;
	Shader generateShader;

	int maxVertices[] = new int[] {500};
	int volumeResolution[] = new int[] {32};
	float baseFrequency[] = new float[] {0.01f};
	float baseAmplitude[] = new float[] {0.5f};
	
	final int vertexSize = 4 * 4;
	
	int generatedVertices = 0;
	
	
	enum Operations{
		GenerateVolume,
		ExtractMesh
	}
	
	Map<Operations, QueryBuffer> timers = Arrays.stream(Operations.values())
		.collect(
			Collectors.toMap(e -> e, e -> new QueryBuffer(GL_TIME_ELAPSED, 10))
		);
	
    public MarchingCubes(){
    	marchingCubesShader = new Shader("resources/shaders/marchingCubes.cs");
    	marchingCubesShader.finishInit();
    	marchingCubesShader.init_uniforms(List.of("SDF_OFFSET", "transform", "chunk"));
    	
    	generateShader = new Shader("resources/shaders/generateTerrain.cs");
    	generateShader.finishInit();
    	generateShader.init_uniforms(List.of("chunk", "baseFrequency", "baseAmplitude"));
    	
    	prepareMarchingCubesBuffer();
    	prepareVolumeTexture();
    	
    	//Make a bigger buffer whose every 16 element is the number of triangles to generate
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
    	System.out.println("Creating vertex buffer with a capacity of "+ maxVertices[0] + "K vertices.");
    	marchingCubesOutput.storeData(maxVertices[0] * 1000 * vertexSize, GL_MAP_READ_BIT);
    }
    
    void prepareVolumeTexture() {
    	int s = volumeResolution[0];
    	System.out.println("Creating volume texture with a resolution of "+ s + ".");
    	if(volumeTexture != null)volumeTexture.delete();
    	volumeTexture = new Texture3D(s, s, s, GL_RGBA16F, GL_RGBA, GL_FLOAT, false, (ByteBuffer)null);
    }
    
    public void prepareNoiseTexture() {
    	int s = 16;
    	if(noiseTexture != null)noiseTexture.delete();
    	
    	float array[] = new float[4*s*s*s];
    	for(int k = 0; k<array.length; k++) {
    		array[k] = (float)Math.random() * 2.0f - 1.0f; 
    	}
    	
    	noiseTexture = new Texture3D(s, s, s, GL_RGBA16_SNORM, GL_RGBA, GL_FLOAT, false, array);
    	noiseTexture.setFilter(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR);
    	noiseTexture.setWrapMode(GL_REPEAT);
    }
    
    void updateGUI() {
    	ImGui.sliderFloat("Base Frequency", baseFrequency, 0, 0.1f);
    	ImGui.sliderFloat("Base Amplitude", baseAmplitude, 0, 1);
    	

    	if(ImGui.treeNode("Marching Cubes")) {
    		
    		if(ImGui.sliderInt("Max Vertices (k)", maxVertices, 10, 1000)) {
    			prepareMarchingCubesBuffer();
    		}
    		if(ImGui.sliderInt("Volume Resolution", volumeResolution, 16, 256)) {
    			prepareVolumeTexture();
    		}

    		ImGui.text(
    				String.format("Generate Volume: %.02f ms", timers.get(Operations.GenerateVolume).getLastResult(true)*1.0E-6f));
    		ImGui.text(
    				String.format("Extract Mesh: %.02f ms", timers.get(Operations.ExtractMesh).getLastResult(true)*1.0E-6f));
    		
    		ImGui.treePop();
    	}
    }
	
    public Chunk extractMesh(Vector3f chunk){
    	final int groups = (volumeTexture.getWidth()+3) / 4;

    	var q = timers.get(Operations.GenerateVolume).push_back();
    	q.begin();
    	generateShader.start();
    	generateShader.loadVec3("chunk", chunk);
    	generateShader.loadFloat("baseFrequency", baseFrequency[0]);
    	generateShader.loadFloat("baseAmplitude", baseAmplitude[0]);
    	glBindImageTexture(0, volumeTexture.getID(), 0, true, 0, GL_WRITE_ONLY, GL_RGBA16F);
    	noiseTexture.bindToTextureUnit(1);
    	glDispatchCompute(groups, groups, groups);
    	noiseTexture.unbindFromTextureUnit(1);
    	generateShader.stop();
    	q.end();
    	
    	glMemoryBarrier(GL_ALL_BARRIER_BITS);
    	
    	int zero[] = new int[] {0};
    	Atomic = new BindlessBuffer();
    	Atomic.storeData(zero, GL_MAP_READ_BIT);
    	
    	q = timers.get(Operations.ExtractMesh).push_back();
    	q.begin();
    	marchingCubesShader.start();
    	marchingCubesShader.loadFloat("SDF_OFFSET", 0.0f);
    	marchingCubesShader.loadMat4("transform", new Matrix4f());
    	marchingCubesShader.loadVec3("chunk", chunk);
    	glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, Atomic.getID());
    	glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, marchingCubesOutput.getID());
    	glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, LUT.getID());
    	glBindImageTexture(0, volumeTexture.getID(), 0, true, 0, GL_READ_ONLY, GL_RGBA16F);
    	glDispatchCompute(groups, groups, groups);
    	glMemoryBarrier(GL_ALL_BARRIER_BITS);
    	marchingCubesShader.stop();
    	q.end();

    	generatedVertices = glMapNamedBuffer(Atomic.getID(), GL_READ_ONLY).asIntBuffer().get();
    	glUnmapNamedBuffer(Atomic.getID());
    	Atomic.delete();
//    	System.out.println("Generated " + generatedVertices + " vertices");
    	
    	if(generatedVertices == 0) {
    		return null;
    	}else {
    		int capacity = Math.min(generatedVertices, maxVertices[0] * 1000) * vertexSize;
    		
    		VAO vao = new VAO();
    		vao.bind();
    		VBO vbo = new VBO(GL_ARRAY_BUFFER);
    		vbo.bind();
    		vbo.reserveData(capacity, GL_STATIC_DRAW);
    		glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, 0);
    		vbo.unbind();
    		vao.unbind();
    		vao.getAttributeVBOs().add(vbo);
    		
    		glCopyNamedBufferSubData(marchingCubesOutput.getID(), vbo.getID(), 0, 0, capacity);
    		
    		return new Chunk(vao, capacity / vertexSize, new Vector3i(chunk, RoundingMode.FLOOR));
    	}
    	
    }
    
    
}
