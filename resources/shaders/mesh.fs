#version 460

layout(origin_upper_left, pixel_center_integer) in vec4 gl_FragCoord;

layout(binding = 0, std430) restrict buffer WorldPosBuffer{
	vec4[] cursor_world_pos;
	int index;
};

out vec4 out_Color;

in vec3 color;
in vec3 worldPos;
in vec3 normal;

uniform vec3 cameraPos;
uniform vec2 mousePos;
uniform vec3 prevCursorWorldPos;

void main(){
	if(vec2(gl_FragCoord.xy) == mousePos){
		int n = atomicAdd(index, 1);
		cursor_world_pos[n] = vec4(worldPos, 1.0);
	}
	
	const vec3 N = normalize(normal);
	const vec3 V = normalize(cameraPos-worldPos);

	float L = max(dot(V, N), 0.0f);

	out_Color = vec4(color * L, 1);
	
	float dist = length(prevCursorWorldPos-worldPos);
	if(dist < 0.2){
		float t = clamp(dist * 2.0, 0.0, 1.0);
		out_Color = mix(out_Color, vec4(1.0, 0.0, 0.0, 0.0), t);
	}
}