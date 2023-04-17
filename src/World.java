
import static org.lwjgl.opengl.GL46C.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;

import imgui.ImGui;
import utils.Shader;
import utils.Texture3D;
import utils.VAO;

public class World {

	int generateDistance[] = new int[] { 5 };
	int chunksPerFrame[] = new int[] { 10 };
	int generatedDistance = 0;
	List<Vector3i> chunksToGenerate = new ArrayList<>();


	float updateStrength[] = new float[] { 0 };
	float updateRadius[] = new float[] { 0 };
	
	int totalVertices = 0;
	Map<Vector3i, Chunk> chunks = new HashMap<>();
	Set<Chunk> nonEmptyChunks = new HashSet<>();
	MarchingCubes marchingCubes = new MarchingCubes();
	Shader meshShader;
	Shader updateChunkShader;

	public World() {
		meshShader = new Shader("resources/shaders/mesh.vs", "resources/shaders/mesh.gs", "resources/shaders/mesh.fs");
		meshShader.finishInit();
		meshShader.init_uniforms(List.of("projection", "view", "transform", "cameraPos"));
		
		updateChunkShader = new Shader("resources/shaders/updateChunk.cs");
		updateChunkShader.finishInit();
		updateChunkShader.init_uniforms(List.of("chunkCoord", "cursorWorldPos", "radius", "strength"));
	}

	public void updateGUI(Vector3fc cameraPos) {
		
		

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
			
			if(ImGui.button("Update Chunks")) {
				updateChunks(new Vector3f(0, 0, 0));
			}
		
			ImGui.treePop();
		}

		ImGui.text("Generated Chunk:" + chunks.size());
		ImGui.text("Non-empty chunks:" + nonEmptyChunks.size());
		ImGui.text("Total vertices:" + totalVertices);

		marchingCubes.updateGUI();
		update(cameraPos);
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
			}
			return true;
		}
		return false;
	}

	public void update(Vector3fc cameraPos) {
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

	void render(Matrix4fc projection, Matrix4fc view, Vector3fc cameraPos) {
		glEnable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);

		meshShader.start();
		meshShader.loadMat4("projection", projection);
		meshShader.loadMat4("view", view);
		meshShader.loadMat4("transform", new Matrix4f());
		meshShader.loadVec3("cameraPos", cameraPos);

		for (Chunk c : nonEmptyChunks) {
			VAO vao = c.vao;
			vao.bind();
			vao.bindAttribute(0);
			glDrawArrays(GL_TRIANGLES, 0, c.vertices);
			vao.unbindAttribute(0);
			vao.unbind();
		}

		meshShader.stop();
		glEnable(GL_CULL_FACE);
	}

	void updateChunks(Vector3f cursorWorldPos) {
		
		List<Chunk> chunksToUpdate = new ArrayList<>();
		
		
		float r = updateRadius[0];
		for(int k=(int)Math.floor(cursorWorldPos.z - r); k < (int)Math.floor(cursorWorldPos.z + r); k++) {
			for(int j=(int)Math.floor(cursorWorldPos.y - r); j < (int)Math.floor(cursorWorldPos.y + r); j++) {
				for(int i=(int)Math.floor(cursorWorldPos.x - r); i < (int)Math.floor(cursorWorldPos.x + r); i++) {
					Chunk c = chunks.get(new Vector3i(i, j, k));
					chunksToUpdate.add(c);
				}
			}
		}
		
		updateChunkShader.start();
		updateChunkShader.loadFloat("strength", updateStrength[0]);
		updateChunkShader.loadFloat("radius", r);
		updateChunkShader.loadVec3("cursorWorldPos", cursorWorldPos);
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
			if(c.vertices == 0) {
				nonEmptyChunks.remove(c);
			}else {
				nonEmptyChunks.add(c);
			}
		}
		
	}

}
