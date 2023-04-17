import org.joml.Vector3i;

import utils.Texture3D;
import utils.VAO;

public class Chunk {
	VAO vao;
	int vertices;
	Vector3i coords;
	Texture3D voxels;
	public Chunk(VAO vao, Texture3D voxels, int vertices, Vector3i coords) {
		this.vao = vao;
		this.voxels = voxels;
		this.vertices = vertices;
		this.coords = coords;
	}
}
