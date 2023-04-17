import org.joml.Vector3i;

import utils.VAO;

public class Chunk {
	VAO vao;
	int vertices;
	Vector3i coords;
	public Chunk(VAO vao, int vertices, Vector3i coords) {
		this.vao = vao;
		this.vertices = vertices;
		this.coords = coords;
	}
}
