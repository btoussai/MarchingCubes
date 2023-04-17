import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;

import imgui.ImGui;
import utils.Shader;
import utils.VAO;

public class World {

	int generateDistance[] = new int[] { 5 };
	int chunksPerFrame[] = new int[] { 10 };
	int generatedDistance = 0;
	List<Vector3i> chunksToGenerate = new ArrayList<>();

	int totalVertices = 0;
	Map<Vector3i, Chunk> chunks = new HashMap<>();
	List<Chunk> nonEmptyChunks = new ArrayList<>();
	MarchingCubes marchingCubes = new MarchingCubes();
	Shader meshShader;

	public World() {
		meshShader = new Shader("resources/shaders/mesh.vs", "resources/shaders/mesh.gs", "resources/shaders/mesh.fs");
		meshShader.finishInit();
		meshShader.init_uniforms(List.of("projection", "view", "transform", "cameraPos"));
	}

	public void updateGUI(Vector3fc cameraPos) {
		
		ImGui.sliderInt("Generate Distance", generateDistance, 0, 20);
		ImGui.sliderInt("Chunks per frame", chunksPerFrame, 1, 30);
		if (ImGui.button("Reset World")) {
			chunks.clear();
			generatedDistance = 0;
			marchingCubes.prepareNoiseTexture();
			chunksToGenerate.clear();
			nonEmptyChunks.clear();
			totalVertices = 0;
		}

		ImGui.text("Generated Chunk:" + chunks.size());
		ImGui.text("Non-empty chunks:" + nonEmptyChunks.size());
		ImGui.text("Total vertices:" + totalVertices);

		marchingCubes.updateGUI();
		update(cameraPos);
	}
	
	private boolean gen(Vector3i coords) {
		if (!chunks.containsKey(coords)) {
			Chunk chunk = marchingCubes.extractMesh(new Vector3f(coords));
			chunks.put(coords, chunk);
			if(chunk != null) {
				nonEmptyChunks.add(chunk);
				totalVertices += chunk.vertices;
			}
			return true;
		}
		return false;
	}

	public void update(Vector3fc cameraPos) {
		if(generatedDistance == generateDistance[0]) {
			return;
		}
		
		if(chunksToGenerate.isEmpty()) {
			int n = generatedDistance;
			
			for (int k = -n; k <= n; k++) {
				if(k == -n || k == n) {
					for (int j = -n; j <= n; j++)
						for (int i = -n; i <= n; i++)
							chunksToGenerate.add(new Vector3i(i,j,k));
				}else {
					for (int j = -n; j <= n; j++) {
						if(j == -n || j == n) {
							for (int i = -n; i <= n; i++)
								chunksToGenerate.add(new Vector3i(i,j,k));
						}else {
							chunksToGenerate.add(new Vector3i(+n,j,k));
							chunksToGenerate.add(new Vector3i(-n,j,k));
						}
					}
				}
			}
		}

		int count = chunksPerFrame[0];
		for(int i=0; i<count; i++) {
			if(chunksToGenerate.size() == 0) {
				break;
			}
			Vector3i coord = chunksToGenerate.remove(chunksToGenerate.size()-1);
			gen(coord);
		}
		
		if(chunksToGenerate.size() == 0) {
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

}
