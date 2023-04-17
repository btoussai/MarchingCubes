#version 460

out vec4 out_Color;

in vec3 color;
in vec3 worldPos;
in vec3 normal;

uniform vec3 cameraPos;

void main(){

	const vec3 N = normalize(normal);
	const vec3 V = normalize(cameraPos-worldPos);

	float L = max(dot(V, N), 0.0f);

	out_Color = vec4(color * L, 1);
	
}