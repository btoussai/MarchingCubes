import cataclysm.DefaultParameters;
import cataclysm.PhysicsWorld;
import cataclysm.wrappers.ConvexHullWrapper;
import cataclysm.wrappers.RigidBody;
import cataclysm.wrappers.WrapperBuilder;
import cataclysm.wrappers.WrapperFactory;

import math.vector.Vector3f;
import utils.Camera;
import utils.Shader;
import utils.VAO;
import static org.lwjgl.opengl.GL46C.*;

import java.util.List;

public class PhysicsManager {
	
	PhysicsWorld physicsWorld;
	Shader debugShader;
	VAO cubeVAO;
	RigidBody cube;
	
	
	PhysicsManager(){
		DefaultParameters physicsParams = new DefaultParameters();
		physicsParams.setTimeStep(1.0f / 120.0f);
		physicsParams.getForceIntegrator().setGravityStrength(0.0f);
		
		physicsWorld = new PhysicsWorld(physicsParams, 1);
		
		WrapperFactory factory = new WrapperFactory();
		WrapperBuilder builder = factory.newBox(1, 1, 1);
		cube = physicsWorld.newBody(new Vector3f(0, 2, 0), builder);
		
		cubeVAO = new VAO();
		cubeVAO.bind();
		var data = ((ConvexHullWrapper)cube.getWrappers().get(0)).getConvexHullData().asModel();
		cubeVAO.createIndexBuffer(data.indices);
		cubeVAO.createFloatAttribute(0, data.vertices, 3, 0, GL_STATIC_DRAW);
		cubeVAO.unbind();
		
		debugShader = new Shader("resources/shaders/physics.vs", "resources/shaders/physics.fs");
		debugShader.finishInit();
		debugShader.init_uniforms(List.of("projection", "view", "transform"));
	}
	
	void update() {
		System.out.println(cube.getPosition());
		physicsWorld.update(1);
		System.out.println(cube.getPosition());
		
		
	}
	
	void render(Camera camera) {
		debugShader.start();
		
//		Matrix4f transform = new Matrix4f();
		

		debugShader.loadMat4("projection", camera.getProjectionMatrix());
		debugShader.loadMat4("view", camera.getViewMatrix());
//		debugShader.loadMat4("transform", transform);
		debugShader.stop();
	}

}
