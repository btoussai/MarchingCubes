#version 460

layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;

uniform layout(binding=0, rgba16_snorm) writeonly restrict image3D Volume;
uniform layout(binding=1) sampler3D NoiseTex;

uniform vec3 chunk;
uniform float baseFrequency;
uniform float baseAmplitude;

const int NoiseSise = 16;

vec4 cubicLerp(vec4 f[4], float t){
	return vec4(1, t, t*t, t*t*t) * 
			mat4(
				vec4(0, -0.5f,     1, -0.5f),
				vec4(1,     0, -2.5f,  1.5f),
				vec4(0,  0.5f,  2.0f, -1.5f),
				vec4(0,     0, -0.5f, 0.5f))
			* transpose(mat4(f[0], f[1], f[2], f[3]));
}

vec4 cubicLerpNoise(vec3 texel){
	const vec3 floor_texel = floor(texel);
	vec3 fract_texel = texel - floor_texel;
	const ivec3 int_texel = ivec3(floor_texel);
	
	vec4 value_z[4];
	for(int k=0; k<=3; k++){
		vec4 value_y[4];
		for(int j=0; j<=3; j++){
			vec4 value_x[4];
			for(int i=0; i<=3; i++){
				ivec3 noiseCoords = int_texel + ivec3(i-1, j-1, k-1);
				value_x[i] = texelFetch(NoiseTex, noiseCoords & (NoiseSise-1), 0);
			}
			value_y[j] = cubicLerp(value_x, fract_texel.x);
		}
		value_z[k] = cubicLerp(value_y, fract_texel.y);
	}
	vec4 value = cubicLerp(value_z, fract_texel.z);
	return value;
}

vec4 sampleOctave(inout vec3 position, float frequency, float amplitude){
	vec3 texel = position * frequency * NoiseSise;
	
	vec4 value = cubicLerpNoise(texel) * amplitude;
	
	position += vec3(value) * frequency * 4;
	
	return value;
}

vec4 square(vec4 x){
	return x*x;
}

void main(){
	
	ivec3 tex_pos = ivec3(gl_GlobalInvocationID); // position of the volume texel
	
	vec3 position = chunk + (tex_pos+0.5f) / (imageSize(Volume)-1); // in [0, 1]
	
	vec4 value = vec4(position.y * 2.0f - 1.0f, 0, 0, 0);
	
	for(int i=0; i<5; i++){
		value += sampleOctave(position, baseFrequency * pow(2.0f, i), baseAmplitude / pow(2.0f, i));
	}
	
	value.yzw = abs(cos(value.yzw));
	
	imageStore(Volume, tex_pos, value);

}