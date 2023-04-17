import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL15C.GL_READ_ONLY;
import static org.lwjgl.opengl.GL30C.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45C.glMapNamedBuffer;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector4f;

import imgui.ImGui;
import utils.BindlessBuffer;
import utils.Camera;
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

	BindlessBuffer WorldPosBuffer;
	Vector3f prevCursorWorldPos = new Vector3f();

	public World() {
		meshShader = new Shader("resources/shaders/mesh.vs", "resources/shaders/mesh.gs", "resources/shaders/mesh.fs");
		meshShader.finishInit();
		meshShader.init_uniforms(
				List.of("projection", "view", "transform", "cameraPos", "mousePos", "prevCursorWorldPos"));
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
			if (chunk != null) {
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

	void render(Camera camera) {
		glEnable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);

		meshShader.start();
		meshShader.loadMat4("projection", camera.getProjectionMatrix());
		meshShader.loadMat4("view", camera.getViewMatrix());
		meshShader.loadMat4("transform", new Matrix4f());
		meshShader.loadVec3("cameraPos", camera.getCameraPos());
//		System.out.println(camera.getMousePos().x() + " ; " + camera.getMousePos().y());
		meshShader.loadVec2("mousePos", camera.getMousePos());
		meshShader.loadVec3("prevCursorWorldPos", prevCursorWorldPos);

		float[] data = new float[20 * 4 + 1];
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

		FloatBuffer fb = glMapNamedBuffer(WorldPosBuffer.getID(), GL_READ_ONLY).asFloatBuffer();
		List<Vector4f> cursorWPList = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			cursorWPList.add(new Vector4f(fb.get(i * 4), fb.get(i * 4 + 1), fb.get(i * 4 + 2), fb.get(i * 4 + 3)));
		}
		cursorWPList.removeIf((v) -> {
			return v.w == 0.0;
		});
		if (!cursorWPList.isEmpty()) {

			Collections.sort(cursorWPList, (v1, v2) -> {
				Vector4f cam = new Vector4f(camera.getCameraPos(), 1.0f);
				return Float.compare(v2.distance(cam), v1.distance(cam));
			});
			cursorWPList.forEach(System.out::println);
			Vector4f closerToCam = cursorWPList.get(0);
			prevCursorWorldPos.set(new Vector3f(closerToCam.x, closerToCam.y, closerToCam.z));
		}
	}

}
