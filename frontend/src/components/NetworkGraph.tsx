import React, { useEffect, useRef } from "react";

interface Node {
  id: string;
  x: number;
  y: number;
  vx: number;
  vy: number;
  label: string;
}

interface Edge {
  source: string;
  target: string;
}

interface NetworkGraphProps {
  nodes: string[];
  edges: Edge[];
}

export const NetworkGraph: React.FC<NetworkGraphProps> = ({ nodes, edges }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const nodesRef = useRef<Map<string, Node>>(new Map<string, Node>());

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Synchronize nodes map with incoming nodes
    nodes.forEach((nodeId) => {
      if (!nodesRef.current.has(nodeId)) {
        nodesRef.current.set(nodeId, {
          id: nodeId,
          x: canvas.width / 2 + (Math.random() - 0.5) * 200,
          y: canvas.height / 2 + (Math.random() - 0.5) * 200,
          vx: 0,
          vy: 0,
          label: (() => {
            try {
              return new URL(nodeId).pathname || "/";
            } catch {
              return nodeId;
            }
          })(),
        });
      }
    });

    let animationFrameId: number;

    const render = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const nodeList = Array.from(nodesRef.current.values());

      // Simple repulsion algorithm O(V^2)
      for (let i = 0; i < nodeList.length; i++) {
        for (let j = i + 1; j < nodeList.length; j++) {
          const dx = nodeList[j].x - nodeList[i].x;
          const dy = nodeList[j].y - nodeList[i].y;
          const dist = Math.sqrt(dx * dx + dy * dy) || 1;
          if (dist < 150) {
            const force = (150 - dist) / 150;
            const fx = (dx / dist) * force * 0.8;
            const fy = (dy / dist) * force * 0.8;
            nodeList[i].vx -= fx;
            nodeList[i].vy -= fy;
            nodeList[j].vx += fx;
            nodeList[j].vy += fy;
          }
        }
      }

      // Spring attraction along edges
      edges.forEach((edge) => {
        const source = nodesRef.current.get(edge.source);
        const target = nodesRef.current.get(edge.target);
        if (source && target) {
          const dx = target.x - source.x;
          const dy = target.y - source.y;
          const dist = Math.sqrt(dx * dx + dy * dy) || 1;
          const force = (dist - 80) * 0.02;
          const fx = (dx / dist) * force;
          const fy = (dy / dist) * force;
          source.vx += fx;
          source.vy += fy;
          target.vx -= fx;
          target.vy -= fy;
        }
      });

      // Update positions with friction damping
      nodeList.forEach((n) => {
        n.vx *= 0.85;
        n.vy *= 0.85;
        n.x += n.vx;
        n.y += n.vy;

        // Keep inside bounds
        n.x = Math.max(20, Math.min(canvas.width - 20, n.x));
        n.y = Math.max(20, Math.min(canvas.height - 20, n.y));
      });

      // Draw Edges
      ctx.strokeStyle = "#334155";
      ctx.lineWidth = 1;
      edges.forEach((edge) => {
        const source = nodesRef.current.get(edge.source);
        const target = nodesRef.current.get(edge.target);
        if (source && target) {
          ctx.beginPath();
          ctx.moveTo(source.x, source.y);
          ctx.lineTo(target.x, target.y);
          ctx.stroke();
        }
      });

      // Draw Nodes
      nodeList.forEach((n) => {
        ctx.beginPath();
        ctx.arc(n.x, n.y, 6, 0, 2 * Math.PI);
        ctx.fillStyle = "#38bdf8";
        ctx.fill();

        ctx.font = "10px sans-serif";
        ctx.fillStyle = "#94a3b8";
        ctx.fillText(n.label.slice(0, 18), n.x + 8, n.y + 3);
      });

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
    };
  }, [nodes, edges]);

  return (
    <div className="w-full h-96 bg-slate-900 border border-slate-800 rounded-xl overflow-hidden relative shadow-inner">
      <div className="absolute top-3 left-3 text-xs font-semibold text-slate-400 uppercase tracking-wider bg-slate-950/70 px-2 py-1 rounded">
        Topology Graph (Force-Directed)
      </div>
      <canvas
        ref={canvasRef}
        width={800}
        height={384}
        className="w-full h-full"
      />
    </div>
  );
};
