
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL15C.GL_READ_ONLY;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL30C.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL31C.GL_RGBA16_SNORM;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.glBindImageTexture;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL45C.glMapNamedBuffer;
import static org.lwjgl.opengl.GL45C.glUnmapNamedBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector4f;

import imgui.ImGui;
import utils.BindlessBuffer;
import utils.Camera;
import utils.Shader;
import utils.Texture3D;
import utils.VAO;

public class World {

	int generateDistance[] = new int[] { 5 };
	int chunksPerFrame[] = new int[] { 10 };
	int generatedDistance = 0;
	List<Vector3i> chunksToGenerate = new ArrayList<>();


	float updateStrength[] = new float[] { 1.0f };
	float updateRadius[] = new float[] { 0.5f };
	
	int totalVertices = 0;
	Map<Vector3i, Chunk> chunks = new HashMap<>();
	Set<Chunk> nonEmptyChunks = new HashSet<>();
	MarchingCubes marchingCubes = new MarchingCubes();
	Shader meshShader;
	Shader updateChunkShader;

	BindlessBuffer WorldPosBuffer;
	Vector3f prevCursorWorldPos = new Vector3f();
	
	PhysicsManager physicsManager;

	public World(PhysicsManager physicsManager) {
		this.physicsManager = physicsManager;
		
		meshShader = new Shader("resources/shaders/mesh.vs", "resources/shaders/mesh.gs", "resources/shaders/mesh.fs");
		meshShader.finishInit();
		meshShader.init_uniforms(
				List.of("projection", "view", "transform", "cameraPos", "mousePos", "prevCursorWorldPos", "radius"));
		
		updateChunkShader = new Shader("resources/shaders/updateChunk.cs");
		updateChunkShader.finishInit();
		updateChunkShader.init_uniforms(List.of("chunkCoord", "cursorWorldPos", "radius", "strength"));
	}

	public void updateGUI(Camera camera) {

		ImGui.sliderInt("Generate Distance", generateDistance, 0, 20);
		ImGui.sliderInt("Chunks per frame", chunksPerFrame, 1, 30);
		if (ImGui.button("Reset World")) {
			for (Chunk c : chunks.values()) {
				if (c.voxels != null)
					c.voxels.delete();
				if (c.vao != null)
					c.vao.delete();
			}
			chunks.clear();
			generatedDistance = 0;
			marchingCubes.prepareNoiseTexture();
			chunksToGenerate.clear();
			nonEmptyChunks.clear();
			totalVertices = 0;
		}
		
		if (ImGui.treeNode("Chunk Update")) {

			ImGui.sliderFloat("Strength", updateStrength, -1, +1);
			ImGui.sliderFloat("Radius", updateRadius, -1, +1);
			
		
			ImGui.treePop();
		}
		if(camera.rmb()) {
			updateChunks();
		}

		ImGui.text("Generated Chunk:" + chunks.size());
		ImGui.text("Non-empty chunks:" + nonEmptyChunks.size());
		ImGui.text("Total vertices:" + totalVertices);

		marchingCubes.updateGUI();
		updateGeneration(camera.getCameraPos());
	}

	private boolean gen(Vector3i coords) {
		if (!chunks.containsKey(coords)) {
			Texture3D voxels = marchingCubes.fillChunkTexture(new Vector3f(coords));
			Chunk chunk = new Chunk(null, voxels, 0, coords);
			marchingCubes.extractMesh(chunk);
			chunks.put(coords, chunk);
			if (chunk.vao != null) {
				nonEmptyChunks.add(chunk);
				totalVertices += chunk.vertices;
				physicsManager.addTriangles(chunk);
			}
			return true;
		}
		return false;
	}

	public void updateGeneration(Vector3fc cameraPos) {
		if (generatedDistance == generateDistance[0]) {
			return;
		}

		if (chunksToGenerate.isEmpty()) {
			int n = generatedDistance;

			for (int k = -n; k <= n; k++) {
				if (k == -n || k == n) {
					for (int j = -n; j <= n; j++)
						for (int i = -n; i <= n; i++)
							chunksToGenerate.add(new Vector3i(i, j, k));
				} else {
					for (int j = -n; j <= n; j++) {
						if (j == -n || j == n) {
							for (int i = -n; i <= n; i++)
								chunksToGenerate.add(new Vector3i(i, j, k));
						} else {
							chunksToGenerate.add(new Vector3i(+n, j, k));
							chunksToGenerate.add(new Vector3i(-n, j, k));
						}
					}
				}
			}
		}

		int count = chunksPerFrame[0];
		for (int i = 0; i < count; i++) {
			if (chunksToGenerate.size() == 0) {
				break;
			}
			Vector3i coord = chunksToGenerate.remove(chunksToGenerate.size() - 1);
			gen(coord);
		}

		if (chunksToGenerate.size() == 0) {
			generatedDistance++;
		}
	}

	void render(Camera camera) {
		glEnable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);

		meshShader.start();
		meshShader.loadMat4("projection", camera.getProjectionMatrix());
		meshShader.loadMat4("view", camera.getViewMatrix());
		meshShader.loadMat4("transform", new Matrix4f());
		meshShader.loadVec3("cameraPos", camera.getCameraPos());
		meshShader.loadVec2("mousePos", camera.getMousePos());
		meshShader.loadVec3("prevCursorWorldPos", prevCursorWorldPos);
		meshShader.loadFloat("radius", updateRadius[0]);

		float[] data = new float[] {1000.0f};
		WorldPosBuffer = new BindlessBuffer();
		WorldPosBuffer.storeData(data, GL_MAP_READ_BIT);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, WorldPosBuffer.getID());

		for (Chunk c : nonEmptyChunks) {
			VAO vao = c.vao;
			vao.bind();
			vao.bindAttribute(0);
			glDrawArrays(GL_TRIANGLES, 0, c.vertices);
			vao.unbindAttribute(0);
			vao.unbind();
		}
		glMemoryBarrier(GL_ALL_BARRIER_BITS);
		meshShader.stop();
		glEnable(GL_CULL_FACE);

		float depth = glMapNamedBuffer(WorldPosBuffer.getID(), GL_READ_ONLY).asFloatBuffer().get(0);
		glUnmapNamedBuffer(WorldPosBuffer.getID());
		
		if(depth != 1000.0f) {
		    float mouseX = camera.getMouseNDC().x();
		    float mouseY = camera.getMouseNDC().y();

		    Matrix4f invVP = camera.getInvViewMatrix().mul(camera.getInvProjMatrix(), new Matrix4f());
		    Vector4f screenPos = new Vector4f(mouseX, mouseY, 1.0f, 1.0f);
		    Vector4f worldPos = screenPos.mul(invVP);

		    Vector3f dir = new Vector3f(worldPos.x, worldPos.y, worldPos.z).normalize().mul(depth);
		    camera.getCameraPos().add(dir, prevCursorWorldPos);
		}
		
	}

	void updateChunks() {
		
		List<Chunk> chunksToUpdate = new ArrayList<>();
		
		Vector3f center = prevCursorWorldPos.add(new Vector3f(0.5f), new Vector3f());
		
		float r = updateRadius[0];
		for(int k=(int)Math.floor(center.z - r); k < (int)Math.ceil(center.z + r); k++) {
			for(int j=(int)Math.floor(center.y - r); j < (int)Math.ceil(center.y + r); j++) {
				for(int i=(int)Math.floor(center.x - r); i < (int)Math.ceil(center.x + r); i++) {
					Chunk c = chunks.get(new Vector3i(i, j, k));
					if(c != null) {
						chunksToUpdate.add(c);
					}
				}
			}
		}
		
		updateChunkShader.start();
		updateChunkShader.loadFloat("strength", updateStrength[0]);
		updateChunkShader.loadFloat("radius", updateRadius[0]);
		updateChunkShader.loadVec3("cursorWorldPos", prevCursorWorldPos);
		for(Chunk c : chunksToUpdate) {
			final int groups = (c.voxels.getWidth() + 3) / 4;
			updateChunkShader.loadVec3("chunkCoord", new Vector3f(c.coords));
			glBindImageTexture(0, c.voxels.getID(), 0, true, 0, GL_READ_WRITE, GL_RGBA16_SNORM);
			glDispatchCompute(groups, groups, groups);
		}
		glMemoryBarrier(GL_ALL_BARRIER_BITS);
		updateChunkShader.stop();

		for(Chunk c : chunksToUpdate) {
			marchingCubes.extractMesh(c);
			physicsManager.addTriangles(c);
			if(c.vertices == 0) {
				nonEmptyChunks.remove(c);
			}else {
				nonEmptyChunks.add(c);
			}
		}
		
	}

}
