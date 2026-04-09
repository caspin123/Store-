import { motion } from 'framer-motion';
import { ShoppingCart } from 'lucide-react';
import { useCartStore } from '../store';
import toast from 'react-hot-toast';

interface Product {
  id: string;
  name: string;
  price: number;
  image: string;
  category: string;
}

export default function ProductCard({ product }: { product: Product }) {
  const addItem = useCartStore((state) => state.addItem);

  const handleAddToCart = () => {
    addItem({
      id: product.id,
      name: product.name,
      price: product.price,
      image: product.image,
    });
    toast.success(`${product.name} added to cart`, {
      style: {
        background: '#1a1500',
        color: '#ffd700',
        border: '1px solid rgba(184,134,11,0.3)',
      },
      iconTheme: {
        primary: '#ffd700',
        secondary: '#1a1500',
      },
    });
  };

  return (
    <motion.div 
      whileHover={{ y: -10 }}
      className="group relative rounded-2xl overflow-hidden glass-gold transition-all duration-300 hover:shadow-[0_0_30px_rgba(255,215,0,0.2)]"
    >
      <div className="aspect-[4/5] overflow-hidden relative">
        <div className="absolute inset-0 bg-gradient-to-t from-surface via-transparent to-transparent z-10 opacity-60"></div>
        <img 
          src={product.image} 
          alt={product.name} 
          className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-110"
        />
        
        {/* Hover overlay with Add to Cart */}
        <div className="absolute inset-0 z-20 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-300 bg-black/40 backdrop-blur-[2px]">
          <button 
            onClick={handleAddToCart}
            className="translate-y-4 group-hover:translate-y-0 transition-all duration-300 flex items-center gap-2 px-6 py-3 rounded-full bg-gold-500 text-black font-semibold hover:bg-gold-400 box-glow-gold"
          >
            <ShoppingCart className="w-4 h-4" /> Add to Cart
          </button>
        </div>
      </div>
      
      <div className="p-5 relative z-20 bg-surface">
        <div className="text-xs text-gold-600 uppercase tracking-wider font-semibold mb-1">{product.category}</div>
        <h3 className="text-lg font-display text-gold-100 mb-2 truncate">{product.name}</h3>
        <div className="text-xl font-medium text-gold-400">${product.price.toFixed(2)}</div>
      </div>
      
      {/* Shimmer effect on hover */}
      <div className="absolute inset-0 -translate-x-full group-hover:animate-shimmer z-30 pointer-events-none"></div>
    </motion.div>
  );
}
