import cataclysm.CataclysmCallbacks;
import cataclysm.DefaultCollisionFilter;
import cataclysm.DefaultParameters;
import cataclysm.PhysicsWorld;
import cataclysm.broadphase.staticmeshes.StaticMesh;
import cataclysm.broadphase.staticmeshes.StaticMeshData;
import cataclysm.contact_creation.ContactProperties;
import cataclysm.integrators.VerticalGravityIntegrator;
import cataclysm.wrappers.ConvexHullWrapper;
import cataclysm.wrappers.RigidBody;
import cataclysm.wrappers.SphereWrapper;
import cataclysm.wrappers.Wrapper;
import cataclysm.wrappers.WrapperBuilder;
import cataclysm.wrappers.WrapperFactory;

import math.vector.Vector3f;
import utils.Camera;
import utils.ModelLoader;
import utils.Shader;
import utils.VAO;
import static org.lwjgl.opengl.GL46C.*;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class PhysicsManager {
	
	class CollisionData{
		VAO vao;
		StaticMesh mesh;
		Chunk chunk;
	}
	class DynamicObject{
		RigidBody body;
		VAO vao;
	}
	
	PhysicsWorld physicsWorld;
	Shader debugShader;
	VAO sphereVAO = ModelLoader.load("resources/models/sphere.obj", 0, false);
	final float scale = 16.0f;

	Map<Chunk, CollisionData> collisionMeshes = new HashMap<>();
	List<DynamicObject> dynamicObjects = new ArrayList<>();
	
	PhysicsManager(){
		DefaultParameters physicsParams = new DefaultParameters(
				1.0f / 120.0f, 
				2, 
				8, 
				16, 
				4, 
				new VerticalGravityIntegrator(), 
				new CataclysmCallbacks(), 
				new DefaultCollisionFilter(), 
				new ContactProperties(0.2f, 0.3f));
		
		physicsWorld = new PhysicsWorld(physicsParams, 1);
		
		WrapperFactory factory = new WrapperFactory();
		WrapperBuilder builder = factory.newSphere(1);
//		WrapperBuilder builder = factory.newBox(1, 1, 1);
		
		for(int i=0; i<10; i++) {
			var e = new DynamicObject();
			e.body = physicsWorld.newBody(new Vector3f(0, 20 + 2 * i, 0), builder);
			e.body.getVelocity().set(0, 0, 1);
			e.body.setSleeping(false);
			e.vao = makeVAO(e.body.getWrappers().get(0));
			dynamicObjects.add(e);
		}
		
		debugShader = new Shader("resources/shaders/physics.vs", "resources/shaders/physics.fs");
		debugShader.finishInit();
		debugShader.init_uniforms(List.of("projection", "view", "transform"));
	}
	
	VAO makeVAO(Wrapper wrapper) {
		if(wrapper instanceof ConvexHullWrapper) {
			var w = ((ConvexHullWrapper)wrapper).getConvexHullData();
			VAO vao = new VAO();
			vao.bind();
			var data = w.asModel();
			vao.createIndexBuffer(data.indices);
			vao.createFloatAttribute(0, data.vertices, 3, 0, GL_STATIC_DRAW);
			vao.unbind();
			return vao;
		}else if(wrapper instanceof SphereWrapper) {
			return sphereVAO;
		}else {
			return null;
		}
	}
	
	VAO makeVAO(StaticMesh mesh) {
		VAO vao = new VAO();
		vao.bind();
		var data = mesh.asModel();
		vao.createIndexBuffer(data.indices);
		vao.createFloatAttribute(0, data.vertices, 3, 0, GL_STATIC_DRAW);
		vao.unbind();
		return vao;
	}
	
	void update() {
//		System.out.println(cube.getPosition());
		physicsWorld.update(1);
//		System.out.println(cube.getPosition());
		
		
	}
	
	void loadFromMat4(math.vector.Matrix4f src, org.joml.Matrix4f dest) {
		dest.set(
				src.m00, src.m01, src.m02, src.m03, 
				src.m10, src.m11, src.m12, src.m13, 
				src.m20, src.m21, src.m22, src.m23, 
				src.m30, src.m31, src.m32, src.m33);
	}
	
	
	void render(Camera camera) {
		glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
		debugShader.start();
		debugShader.loadMat4("projection", camera.getProjectionMatrix());
		debugShader.loadMat4("view", camera.getViewMatrix());
		
		org.joml.Matrix4f transform = new org.joml.Matrix4f();

		for(var e : dynamicObjects) {
			e.body.setSleeping(false);
			e.body.setSleepCounter(0);
			
			var R = e.body.getOriginTransform().getRotation();
			var T = e.body.getOriginTransform().getTranslation();
			transform.set(
					R.m00, R.m01, R.m02, 0, 
					R.m10, R.m11, R.m12, 0, 
					R.m20, R.m21, R.m22, 0, 
					T.x, T.y, T.z, 1);
			transform.scaleLocal(1.0f / scale);
			
			debugShader.loadMat4("transform", transform);
			
			e.vao.bind();
			e.vao.bindAttribute(0);
			glDrawElements(GL_TRIANGLES, e.vao.getIndexCount(), GL_UNSIGNED_INT, 0);
			e.vao.unbindAttribute(0);
			e.vao.unbind();
		}

//		for(var e : collisionMeshes.values()) {
//			loadFromMat4(e.mesh.getTransform(), transform);
//			transform.scaleLocal(1.0f / scale);
//			debugShader.loadMat4("transform", transform);
//			e.vao.bind();
//			e.vao.bindAttribute(0);
//			glDrawElements(GL_TRIANGLES, e.vao.getIndexCount(), GL_UNSIGNED_INT, 0);
//			e.vao.unbindAttribute(0);
//			e.vao.unbind();
//		}
		glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
		
		debugShader.stop();
	}

	public void addTriangles(Chunk c) {
		
		if(collisionMeshes.containsKey(c)) {
			physicsWorld.deleteMesh(collisionMeshes.get(c).mesh.getID());
		}
		if(c.vertices_array == null) {
			return;
		}
		
		float[] vertices_float = c.vertices_array;
		
		List<Integer> indices = new ArrayList<>();
		List<Vector3f> vertices = new ArrayList<>();
		for(int i=0; i<vertices_float.length/9; i++) {
			Vector3f v1 = new Vector3f(vertices_float[9*i+0], vertices_float[9*i+1], vertices_float[9*i+2]);
			Vector3f v2 = new Vector3f(vertices_float[9*i+3], vertices_float[9*i+4], vertices_float[9*i+5]);
			Vector3f v3 = new Vector3f(vertices_float[9*i+6], vertices_float[9*i+7], vertices_float[9*i+8]);

			v1.scale(scale);
			v2.scale(scale);
			v3.scale(scale);

			Vector3f e0 = Vector3f.sub(v2, v1);
			Vector3f e1 = Vector3f.sub(v3, v1);
			
			Vector3f.cross(e0, e1).length();

			float length = Vector3f.cross(e0, e1).length();
			if (length < 1E-6f) {
				continue;
			}
			
			vertices.add(v1);
			vertices.add(v2);
			vertices.add(v3);
			indices.add(indices.size());
			indices.add(indices.size());
			indices.add(indices.size());
		}
		
		StaticMeshData data = new StaticMeshData(
				indices.stream().mapToInt(Integer::intValue).toArray(), 
				vertices.stream().toArray(k -> new Vector3f[k]));
		StaticMesh mesh = physicsWorld.newMesh(data, new math.vector.Matrix4f(), true);
		
		VAO vao = makeVAO(mesh);
		
		CollisionData collisionData = new CollisionData();
		collisionData.chunk = c;
		collisionData.mesh = mesh;
		collisionData.vao = vao;
		
		collisionMeshes.put(c, collisionData);
	}

}
