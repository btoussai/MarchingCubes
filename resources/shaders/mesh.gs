#version 460

layout(triangles) in;
layout(triangle_strip, max_vertices=3) out;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 transform;

out vec3 color;
out vec3 normal;
out vec3 worldPos;

void main(){
	uint c0 = floatBitsToUint(gl_in[0].gl_Position.w);
	uint c1 = floatBitsToUint(gl_in[1].gl_Position.w);
	uint c2 = floatBitsToUint(gl_in[2].gl_Position.w);

	vec4 p0 = vec4(vec3(gl_in[0].gl_Position), 1.0f);
    vec4 p1 = vec4(vec3(gl_in[1].gl_Position), 1.0f);
	vec4 p2 = vec4(vec3(gl_in[2].gl_Position), 1.0f);

	normal = vec3(transform * vec4(cross(vec3(p1)-vec3(p0), vec3(p2)-vec3(p0)), 0.0f));
	normal = normalize(normal);

	p0 = transform * p0;
	p1 = transform * p1;
	p2 = transform * p2;
	

	color = vec3(unpackUnorm4x8(c0));
	worldPos = vec3(p0);
	gl_Position = projection * view * p0;
	EmitVertex();

	color = vec3(unpackUnorm4x8(c1));
	worldPos = vec3(p1);
	gl_Position = projection * view * p1;
	EmitVertex();
	
	color = vec3(unpackUnorm4x8(c2));
	worldPos = vec3(p2);
	gl_Position = projection * view * p2;
	EmitVertex();
	
	EndPrimitive();
	
}