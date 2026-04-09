import { motion, AnimatePresence } from 'framer-motion';
import { X, Minus, Plus, Trash2 } from 'lucide-react';
import { useCartStore } from '../store';

export default function CartDrawer() {
  const { isOpen, items, toggleCart, updateQuantity, removeItem } = useCartStore();

  const total = items.reduce((sum, item) => sum + item.price * item.quantity, 0);

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={toggleCart}
            className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50"
          />

          {/* Drawer */}
          <motion.div
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            className="fixed top-0 right-0 h-full w-full max-w-md bg-surface border-l border-gold-600/30 shadow-2xl z-50 flex flex-col"
          >
            {/* Header */}
            <div className="flex items-center justify-between p-6 border-b border-gold-600/20">
              <h2 className="font-display text-2xl text-gold-400">Your Cart</h2>
              <button onClick={toggleCart} className="text-gold-100 hover:text-gold-400 transition-colors">
                <X className="w-6 h-6" />
              </button>
            </div>

            {/* Items */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {items.length === 0 ? (
                <div className="text-center text-gold-100/60 mt-10">
                  Your cart is empty.
                </div>
              ) : (
                items.map((item) => (
                  <div key={item.id} className="flex gap-4 bg-gold-900/20 p-4 rounded-xl border border-gold-600/10">
                    <img src={item.image} alt={item.name} className="w-20 h-20 object-cover rounded-lg border border-gold-600/30" />
                    <div className="flex-1 flex flex-col justify-between">
                      <div className="flex justify-between">
                        <h3 className="font-medium text-gold-100">{item.name}</h3>
                        <button onClick={() => removeItem(item.id)} className="text-red-400 hover:text-red-300">
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                      <div className="text-gold-400 font-medium">${item.price.toFixed(2)}</div>
                      <div className="flex items-center gap-3 mt-2">
                        <button 
                          onClick={() => updateQuantity(item.id, item.quantity - 1)}
                          className="w-6 h-6 rounded-full bg-gold-900 flex items-center justify-center text-gold-400 hover:bg-gold-800"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <span className="text-gold-100 text-sm w-4 text-center">{item.quantity}</span>
                        <button 
                          onClick={() => updateQuantity(item.id, item.quantity + 1)}
                          className="w-6 h-6 rounded-full bg-gold-900 flex items-center justify-center text-gold-400 hover:bg-gold-800"
                        >
                          <Plus className="w-3 h-3" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Footer */}
            <div className="p-6 border-t border-gold-600/20 bg-surface/80 backdrop-blur-md">
              <div className="flex justify-between items-center mb-6">
                <span className="text-gold-100 font-medium">Total</span>
                <span className="text-2xl font-display text-gold-400">${total.toFixed(2)}</span>
              </div>
              <button 
                disabled={items.length === 0}
                className="w-full py-4 rounded-xl bg-gold-500 text-black font-bold uppercase tracking-wider hover:bg-gold-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed box-glow-gold"
              >
                Checkout
              </button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
