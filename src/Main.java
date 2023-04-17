import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL46C.*;

import java.nio.IntBuffer;
import java.util.List;
import java.util.Objects;

import imgui.ImGui;
import imgui.app.Application;
import imgui.app.Configuration;
import utils.Camera;
import utils.ModelLoader;
import utils.Shader;
import utils.VAO;

public class Main extends Application {

	VAO grid;
	Shader shader;
	Camera camera;
	long window;

	World world;

	@Override
	protected void configure(Configuration config) {
		config.setTitle("Dear ImGui is Awesome!");
	}

	@Override
	protected void initWindow(Configuration config) {
		GLFWErrorCallback.createPrint(System.err).set();

		if (!GLFW.glfwInit()) {
			throw new IllegalStateException("Unable to initialize GLFW");
		}

		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_SAMPLES, 16);
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		handle = GLFW.glfwCreateWindow(config.getWidth(), config.getHeight(), config.getTitle(), MemoryUtil.NULL,
				MemoryUtil.NULL);

		if (handle == MemoryUtil.NULL) {
			throw new RuntimeException("Failed to create the GLFW window");
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			final IntBuffer pWidth = stack.mallocInt(1); // int*
			final IntBuffer pHeight = stack.mallocInt(1); // int*

			GLFW.glfwGetWindowSize(handle, pWidth, pHeight);
			final GLFWVidMode vidmode = Objects.requireNonNull(GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor()));
			GLFW.glfwSetWindowPos(handle, (vidmode.width() - pWidth.get(0)) / 2,
					(vidmode.height() - pHeight.get(0)) / 2);
		}

		GLFW.glfwMakeContextCurrent(handle);

		GL.createCapabilities();

		GLFW.glfwSwapInterval(GLFW.GLFW_TRUE);

		if (config.isFullScreen()) {
			GLFW.glfwMaximizeWindow(handle);
		} else {
			GLFW.glfwShowWindow(handle);
		}

		glClearColor(colorBg.getRed(), colorBg.getGreen(), colorBg.getBlue(), colorBg.getAlpha());
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		GLFW.glfwSwapBuffers(handle);
		GLFW.glfwPollEvents();

		GLFW.glfwSetWindowSizeCallback(handle, new GLFWWindowSizeCallback() {
			@Override
			public void invoke(final long window, final int width, final int height) {
				runFrame();
			}
		});
	}

	@Override
	protected void preRun() {
		window = glfwGetCurrentContext();
		int major = glGetInteger(GL_MAJOR_VERSION);
		int minor = glGetInteger(GL_MINOR_VERSION);

		String renderer = glGetString(GL_RENDERER);

		System.out.println("Renderer: " + renderer);
		System.out.println("OpenGL version: " + major + "." + minor);

		grid = ModelLoader.load("resources/models/plane_16.obj", 0, true);
		shader = new Shader("resources/shaders/grid.vs", "resources/shaders/grid.fs");
		shader.finishInit();
		shader.init_uniforms(List.of("projection", "view", "transform"));

		camera = new Camera();

		world = new World();

		super.preRun();
	}

	@Override
	public void process() {
		ImGui.text("Hello, World!");
		if(ImGui.checkbox("Centered view", camera.isFreeCam())){
			camera.setFreeCam(!camera.isFreeCam());
		};

		int width[] = new int[1];
		int height[] = new int[1];
		glfwGetFramebufferSize(window, width, height);

		glViewport(0, 0, width[0], height[0]);

		camera.updateView(width[0], height[0]);
		world.updateGUI(camera.getCameraPos());

//		glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
//		shader.start();
//		shader.loadMat4("projection", camera.getProjectionMatrix());
//		shader.loadMat4("view", camera.getViewMatrix());
//		shader.loadMat4("transform", new Matrix4f());
//		grid.bind();
//		grid.bindAttribute(0);
//		glDrawElements(GL_TRIANGLES, grid.getIndexCount(), GL_UNSIGNED_INT, 0);
//		grid.unbindAttribute(0);
//		grid.unbind();
//		shader.stop();
//		glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

		world.render(camera.getProjectionMatrix(), camera.getViewMatrix(), camera.getCameraPos());

	}

	public static void main(String[] args) {
		launch(new Main());
	}
}
