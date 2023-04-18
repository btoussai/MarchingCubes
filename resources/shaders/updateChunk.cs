#version 460
//#extension GL_KHR_shader_subgroup_arithmetic : enable
//#extension GL_KHR_shader_subgroup_basic : enable
//#extension GL_KHR_shader_subgroup_ballot : enable

layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;

uniform layout(binding=0, rgba16_snorm) restrict image3D Volume;

uniform vec3 chunkCoord;
uniform vec3 cursorWorldPos;
uniform float radius;
uniform float strength;

void main(void){

	vec3 worldPos = chunkCoord +
		vec3(gl_GlobalInvocationID) / (imageSize(Volume)-1) - 0.5f;

	vec4 v = imageLoad(Volume, ivec3(gl_GlobalInvocationID));
	
	float d = max(radius - distance(worldPos, cursorWorldPos), 0.0f);
	
	v.x += strength * d * d;
	
	imageStore(Volume, ivec3(gl_GlobalInvocationID), v);	
}