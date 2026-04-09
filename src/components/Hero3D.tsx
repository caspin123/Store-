import { useRef, useState } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Text3D, Center, Sparkles, Float, Environment } from '@react-three/drei';
import * as THREE from 'three';

function AnimatedText() {
  const textRef = useRef<THREE.Mesh>(null);
  const [hovered, setHovered] = useState(false);

  useFrame((state) => {
    if (textRef.current) {
      // Slow auto-rotation
      textRef.current.rotation.y = Math.sin(state.clock.elapsedTime * 0.5) * 0.1;
      
      // Mouse tracking parallax
      const targetX = (state.pointer.x * Math.PI) / 10;
      const targetY = (state.pointer.y * Math.PI) / 10;
      
      textRef.current.rotation.x += 0.05 * (targetY - textRef.current.rotation.x);
      textRef.current.rotation.y += 0.05 * (targetX - textRef.current.rotation.y);
    }
  });

  return (
    <Center>
      <Float speed={2} rotationIntensity={0.2} floatIntensity={0.5}>
        <Text3D
          ref={textRef}
          font="https://threejs.org/examples/fonts/helvetiker_bold.typeface.json"
          size={1.5}
          height={0.4}
          curveSegments={12}
          bevelEnabled
          bevelThickness={0.05}
          bevelSize={0.02}
          bevelOffset={0}
          bevelSegments={5}
          onPointerOver={() => setHovered(true)}
          onPointerOut={() => setHovered(false)}
        >
          Dahabstore
          <meshStandardMaterial 
            color="#FFD700" 
            metalness={1.0} 
            roughness={0.1}
            emissive={hovered ? "#B8860B" : "#000000"}
            emissiveIntensity={0.5}
          />
        </Text3D>
      </Float>
    </Center>
  );
}

export default function Hero3D() {
  return (
    <div className="w-full h-[60vh] md:h-[80vh] relative">
      <Canvas camera={{ position: [0, 0, 8], fov: 45 }}>
        <ambientLight intensity={0.2} />
        <pointLight position={[10, 10, 10]} color="#FFD700" intensity={1} />
        <pointLight position={[-10, -10, -10]} color="#FFF8DC" intensity={0.5} />
        
        <AnimatedText />
        
        <Sparkles 
          count={100} 
          scale={12} 
          size={2} 
          speed={0.4} 
          opacity={0.5} 
          color="#FFD700" 
        />
        
        <Environment preset="city" />
      </Canvas>
    </div>
  );
}
