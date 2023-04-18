#version 460

layout(origin_upper_left, pixel_center_integer) in vec4 gl_FragCoord;

layout(binding = 0, std430) restrict buffer WorldPosBuffer{
	uint depth;
};

out vec4 out_Color;

flat in vec3 color;
in vec3 worldPos;
in vec3 normal;

uniform vec3 cameraPos;
uniform vec2 mousePos;
uniform vec3 prevCursorWorldPos;
uniform float radius;

void main(){
	if(ivec2(gl_FragCoord.xy) == ivec2(mousePos)){
		atomicMin(depth, floatBitsToUint(distance(cameraPos, worldPos)));
	}
	
	const vec3 N = normalize(normal);
	const vec3 V = normalize(cameraPos-worldPos);

	float L = max(dot(V, N), 0.0f);

	out_Color = vec4(color * L, 1);
	
	float dist2 = dot(prevCursorWorldPos-worldPos, prevCursorWorldPos-worldPos);
	if(dist2 < radius*radius){
		float t = 1.0f - min(dist2 / (radius*radius), 1.0f);
		out_Color = mix(out_Color, vec4(1.0, 0.0, 0.0, 0.0), t);
	}
}