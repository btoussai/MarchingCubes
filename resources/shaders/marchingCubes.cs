#version 460
//#extension GL_KHR_shader_subgroup_arithmetic : enable
//#extension GL_KHR_shader_subgroup_basic : enable
//#extension GL_KHR_shader_subgroup_ballot : enable

layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;

uniform layout(binding=0, rgba16_snorm) readonly restrict image3D Volume;

layout(binding = 0, std430) restrict buffer AtomicsBlock{
	int GlobalVertexCounter;
	int emptyVoxels;
	int filledVoxels;
};

layout(binding = 1, std430) restrict writeonly buffer VertexBlock{
	vec4 vertices[];
};

layout(std430, binding = 2) restrict readonly buffer LUT
{
	//At most 5 faces having 3 vertices each, 256 possibilities + the number of triangles generated for each case
	int facesLUT[256*5*3+256];
};

uniform float SDF_OFFSET;
uniform mat4 transform;
uniform vec3 chunk;

shared int shared_array[64];
shared int shared_emptyVoxels[64];
shared int shared_filledVoxels[64];
shared int shared_globalOffset;

float progression(vec4 sdf_color1, vec4 sdf_color2, out vec3 color){// returns value in [-1, 1]
	float d = (sdf_color1.x + sdf_color2.x) / (sdf_color1.x - sdf_color2.x);
	color = 0.5f*( (sdf_color1 + sdf_color2) + d * (sdf_color1 - sdf_color2) ).yzw;
	return d;
}

vec3 makeVertex(int edge, in vec4 sdf_color[8], out vec3 color) {
	switch(edge){
		case 0:
		{
			float d = progression(sdf_color[0], sdf_color[1], color);
			return vec3(-1, +d, -1);
		}
		case 1:
		{
			float d = progression(sdf_color[1], sdf_color[2], color);
			return vec3(+d, +1, -1);
		}
		case 2:
		{
			float d = progression(sdf_color[3], sdf_color[2], color);
			return vec3(+1, +d, -1); 
		}
		case 3:
		{
			float d = progression(sdf_color[0], sdf_color[3], color);
			return vec3(+d, -1, -1);
		}
		case 4:
		{
			float d = progression(sdf_color[4], sdf_color[5], color);
			return vec3(-1, +d, +1);
		}
		case 5:
		{
			float d = progression(sdf_color[5], sdf_color[6], color);
			return vec3(+d, +1, +1);
		}
		case 6:
		{
			float d = progression(sdf_color[7], sdf_color[6], color);
			return vec3(+1, +d, +1);
		}
		case 7:
		{
			float d = progression(sdf_color[4], sdf_color[7], color);
			return vec3(+d, -1, +1);
		}
		case 8:
		{
			float d = progression(sdf_color[0], sdf_color[4], color);
			return vec3(-1, -1, +d);
		}
		case 9:
		{
			float d = progression(sdf_color[1], sdf_color[5], color);
			return vec3(-1, +1, +d);
		}
		case 10:
		{
			float d = progression(sdf_color[2], sdf_color[6], color);
			return vec3(+1, +1, +d);
		}
		case 11:
		{
			float d = progression(sdf_color[3], sdf_color[7], color);
			return vec3(+1, -1, +d);
		}
	}
	return vec3(0.0f);
}

void main(void){
	const ivec3 cornerLocs[8] = {
		ivec3(0,0,0),
		ivec3(0,1,0),
		ivec3(1,1,0),
		ivec3(1,0,0),
		ivec3(0,0,1),
		ivec3(0,1,1),
		ivec3(1,1,1),
		ivec3(1,0,1)
	};
	
	int voxelCase = 0;
	vec4 values[8];
	bool undefinedValues = false;
	for(int i=0; i<8; i++){
		const ivec3 c = ivec3(gl_GlobalInvocationID) + cornerLocs[i];
		vec4 value = vec4(0, 0, 0, 0);
		if (all(lessThan(c, imageSize(Volume)))) {
			value = imageLoad(Volume, c);
			value.x += SDF_OFFSET;
		}else{
			undefinedValues = true;
		}
		
		values[i] = value;
		voxelCase |= (value.x > 0.0f ? 1<<i : 0);
	}

	int triangles = undefinedValues ? 0 : facesLUT[voxelCase * 16 + 0];// # of triangles to generate
	
	shared_array[gl_LocalInvocationIndex] = triangles * 3;
	shared_emptyVoxels[gl_LocalInvocationIndex] = voxelCase==0 ? 1 : 0;
	shared_filledVoxels[gl_LocalInvocationIndex] = voxelCase==255 ? 1 : 0;
	
	memoryBarrier();
	barrier();
	
	int offset = 0;
	int localEmptyVoxels = 0;
	int localFilledVoxels = 0;
	for(int i=0; i<gl_LocalInvocationIndex; i++){
		offset += shared_array[i];
		localEmptyVoxels += shared_emptyVoxels[i];
		localFilledVoxels += shared_filledVoxels[i];
	}
	
	if(gl_LocalInvocationIndex == 63){
		shared_globalOffset = atomicAdd(GlobalVertexCounter, offset + triangles*3);
		atomicAdd(emptyVoxels, localEmptyVoxels);
		atomicAdd(filledVoxels, localFilledVoxels);
	}
	
	memoryBarrier();
	barrier();
	
	offset += shared_globalOffset;
	
	//int offset = subgroupExclusiveAdd(triangles*3);
	//int globalOffset = 0;
	//if(gl_SubgroupInvocationID == 31){
	//	globalOffset = atomicAdd(GlobalVertexCounter, offset + triangles*3);
	//}
	//offset += subgroupBroadcast(globalOffset, 31);
	
	for(int k=0; k<triangles; k++){

		vec3 color;
		vec3 p0 = makeVertex(facesLUT[voxelCase * 16 + k*3 + 1], values, color);
		uint c0 = packUnorm4x8(vec4(color, 1));
		vec3 p1 = makeVertex(facesLUT[voxelCase * 16 + k*3 + 2], values, color);
		uint c1 = packUnorm4x8(vec4(color, 1));
		vec3 p2 = makeVertex(facesLUT[voxelCase * 16 + k*3 + 3], values, color);
		uint c2 = packUnorm4x8(vec4(color, 1));
		
		const vec3 voxelCenter = vec3(gl_GlobalInvocationID + 1.0f);
		const vec3 voxel_size = 1.0f / (imageSize(Volume)-1);

		p2 = chunk + (voxel_size * (p2*0.5f + voxelCenter)) - 0.5f;
		p1 = chunk + (voxel_size * (p1*0.5f + voxelCenter)) - 0.5f;
		p0 = chunk + (voxel_size * (p0*0.5f + voxelCenter)) - 0.5f;

		vertices[offset + k*3 + 0] = vec4(p2, uintBitsToFloat(c2));
		vertices[offset + k*3 + 1] = vec4(p1, uintBitsToFloat(c1));
		vertices[offset + k*3 + 2] = vec4(p0, uintBitsToFloat(c0));

	}
	
	
}