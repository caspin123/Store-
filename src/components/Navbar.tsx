import { useState } from 'react';
import { ShoppingCart, User, Menu, X, LogOut, Shield } from 'lucide-react';
import { useAuthStore, useCartStore } from '../store';
import { motion, AnimatePresence } from 'framer-motion';

export default function Navbar({ onNavigate }: { onNavigate: (page: string) => void }) {
  const { user, setUser } = useAuthStore();
  const { items, toggleCart } = useCartStore();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const handleLogout = async () => {
    await fetch('/api/auth/logout', { method: 'POST' });
    setUser(null);
    onNavigate('home');
  };

  const cartItemCount = items.reduce((acc, item) => acc + item.quantity, 0);

  return (
    <nav className="fixed top-0 w-full z-50 glass-gold border-b border-gold-600/20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-20">
          {/* Logo */}
          <div 
            className="flex-shrink-0 flex items-center cursor-pointer"
            onClick={() => onNavigate('home')}
          >
            <span className="font-display font-bold text-2xl text-gold-400 tracking-wider text-glow-gold">
              DAHABSTORE
            </span>
          </div>

          {/* Desktop Menu */}
          <div className="hidden md:flex items-center space-x-8">
            <button onClick={() => onNavigate('home')} className="text-gold-100 hover:text-gold-400 transition-colors relative group">
              Home
              <span className="absolute -bottom-1 left-0 w-0 h-0.5 bg-gold-400 transition-all group-hover:w-full"></span>
            </button>
            <button onClick={() => onNavigate('store')} className="text-gold-100 hover:text-gold-400 transition-colors relative group">
              Store
              <span className="absolute -bottom-1 left-0 w-0 h-0.5 bg-gold-400 transition-all group-hover:w-full"></span>
            </button>
            
            {user?.role === 'admin' && (
              <button onClick={() => onNavigate('admin')} className="flex items-center text-gold-100 hover:text-gold-400 transition-colors">
                <Shield className="w-4 h-4 mr-1" /> Admin
              </button>
            )}
          </div>

          {/* Icons */}
          <div className="hidden md:flex items-center space-x-6">
            <button 
              onClick={toggleCart}
              className="text-gold-100 hover:text-gold-400 transition-colors relative"
            >
              <ShoppingCart className="w-6 h-6" />
              {cartItemCount > 0 && (
                <span className="absolute -top-2 -right-2 bg-gold-600 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
                  {cartItemCount}
                </span>
              )}
            </button>

            {user ? (
              <div className="flex items-center space-x-4 relative group">
                <div className="flex items-center space-x-2 cursor-pointer">
                  {user.image ? (
                    <img src={user.image} alt={user.name || 'User'} className="w-8 h-8 rounded-full border border-gold-400" />
                  ) : (
                    <div className="w-8 h-8 rounded-full bg-gold-900 border border-gold-400 flex items-center justify-center">
                      <User className="w-4 h-4 text-gold-400" />
                    </div>
                  )}
                  <span className="text-sm text-gold-100">{user.name?.split(' ')[0]}</span>
                </div>
                
                {/* Dropdown */}
                <div className="absolute top-full right-0 mt-2 w-48 rounded-md shadow-lg py-1 bg-surface border border-gold-600/30 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200">
                  <button 
                    onClick={handleLogout}
                    className="flex items-center w-full px-4 py-2 text-sm text-gold-100 hover:bg-gold-900/50"
                  >
                    <LogOut className="w-4 h-4 mr-2" /> Sign Out
                  </button>
                </div>
              </div>
            ) : (
              <button 
                onClick={() => onNavigate('login')}
                className="px-4 py-2 rounded-full border border-gold-400 text-gold-400 hover:bg-gold-400 hover:text-black transition-all duration-300 box-glow-gold"
              >
                Sign In
              </button>
            )}
          </div>

          {/* Mobile menu button */}
          <div className="md:hidden flex items-center space-x-4">
            <button onClick={toggleCart} className="text-gold-100 relative">
              <ShoppingCart className="w-6 h-6" />
              {cartItemCount > 0 && (
                <span className="absolute -top-2 -right-2 bg-gold-600 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
                  {cartItemCount}
                </span>
              )}
            </button>
            <button 
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              className="text-gold-100 hover:text-gold-400"
            >
              {isMobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Menu */}
      <AnimatePresence>
        {isMobileMenuOpen && (
          <motion.div 
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="md:hidden bg-surface border-b border-gold-600/20"
          >
            <div className="px-2 pt-2 pb-3 space-y-1 sm:px-3">
              <button onClick={() => { onNavigate('home'); setIsMobileMenuOpen(false); }} className="block w-full text-left px-3 py-2 text-gold-100 hover:bg-gold-900/50 rounded-md">Home</button>
              <button onClick={() => { onNavigate('store'); setIsMobileMenuOpen(false); }} className="block w-full text-left px-3 py-2 text-gold-100 hover:bg-gold-900/50 rounded-md">Store</button>
              {user?.role === 'admin' && (
                <button onClick={() => { onNavigate('admin'); setIsMobileMenuOpen(false); }} className="block w-full text-left px-3 py-2 text-gold-100 hover:bg-gold-900/50 rounded-md">Admin Dashboard</button>
              )}
              {user ? (
                <button onClick={() => { handleLogout(); setIsMobileMenuOpen(false); }} className="block w-full text-left px-3 py-2 text-gold-100 hover:bg-gold-900/50 rounded-md">Sign Out</button>
              ) : (
                <button onClick={() => { onNavigate('login'); setIsMobileMenuOpen(false); }} className="block w-full text-left px-3 py-2 text-gold-400 font-medium">Sign In</button>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </nav>
  );
}
