#version 460

layout(location=0) in vec4 position;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 transform;

void main(){

	gl_Position = position;

}